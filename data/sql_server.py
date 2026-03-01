#!/usr/bin/env python3
import socket
import sys
import threading
import sqlite3
import os

SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"
DB_FILE = "stomp_server.db"

def init_database():
    """Initializes the database tables."""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()

    # 1. Users Table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            username TEXT PRIMARY KEY,
            password TEXT NOT NULL
        )
    """)

    # 2. Logins Table (History)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS logins (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            logout_time DATETIME
        )
    """)

    # 3. File Tracking (Reports)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            filename TEXT NOT NULL,
            upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            game_channel TEXT
        )
    """)
    
    # הוספת משתמשי ברירת מחדל אם הטבלה ריקה
    cursor.execute("SELECT count(*) FROM users")
    if cursor.fetchone()[0] == 0:
        users = [('meni', 'films'), ('fawzi', '1234'), ('netanel', 'root'), ('tom', 'java')]
        cursor.executemany("INSERT INTO users (username, password) VALUES (?, ?)", users)
        print(f"[{SERVER_NAME}] Inserted default users.")

    conn.commit()
    conn.close()

def handle_client(client_socket, addr):
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    try:
        # קריאת הנתונים עד תו ה-Null (כמו בפרוטוקול ה-Java)
        data = b""
        while True:
            chunk = client_socket.recv(1024)
            if not chunk: break
            data += chunk
            if b'\0' in data: break
            
        sql_query = data.decode('utf-8').replace('\0', '').strip()
        
        if not sql_query:
            return

        print(f"[{SERVER_NAME}] Executing: {sql_query}")
        
        # ביצוע השאילתה
        try:
            if sql_query.upper().startswith("SELECT"):
                cursor.execute(sql_query)
                rows = cursor.fetchall()
                # המרת התוצאה למחרוזת
                response = "SUCCESS|" + "|".join([str(row) for row in rows])
            else:
                cursor.execute(sql_query)
                conn.commit()
                response = "SUCCESS"
        except sqlite3.Error as e:
            response = f"ERROR: {e}"

        # שליחת התשובה חזרה ל-Java (עם Null בסוף)
        client_socket.sendall(response.encode('utf-8') + b'\0')

    except Exception as e:
        print(f"[{SERVER_NAME}] Error: {e}")
    finally:
        conn.close()
        client_socket.close()

def start_server(host="127.0.0.1", port=7778):
    init_database()
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server_socket.bind((host, port))
    except OSError:
        print(f"[{SERVER_NAME}] Port {port} is busy. Maybe server is already running?")
        return

    server_socket.listen(5)
    print(f"[{SERVER_NAME}] Listening on {host}:{port}")

    try:
        while True:
            client, addr = server_socket.accept()
            threading.Thread(target=handle_client, args=(client, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("\nStopping server...")

if __name__ == "__main__":
    start_server()