#include <stdlib.h>
#include <iostream>
#include <thread>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

using namespace std;

StompProtocol protocol;

void readSocketTask(ConnectionHandler* connectionHandler) {
    while (true) {
        string answer;
        if (!connectionHandler->getFrameAscii(answer, '\0')) {
            cout << "Disconnected from server (Socket error)." << endl;
            protocol.setIsConnected(false);
            break;
        }

        bool shouldContinue = protocol.processServerFrame(answer);
        if (!shouldContinue) {
            cout << "Exiting..." << endl;
            break;
        }
    }
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        cerr << "Usage: " << argv[0] << " host port" << endl << endl;
        return -1;
    }
    
    string host = argv[1];
    short port = atoi(argv[2]);

    ConnectionHandler connectionHandler(host, port);
    if (!connectionHandler.connect()) {
        cerr << "Cannot connect to " << host << ":" << port << endl;
        return 1;
    }

    cout << "Connected to socket. Ready for login command." << endl;

    thread socketThread(readSocketTask, &connectionHandler);

    while (true) {
        const short bufsize = 1024;
        char buf[bufsize];
        cin.getline(buf, bufsize);
        string line(buf);
        
        vector<string> framesToSend = protocol.processKeyboardCommand(line);
        
        for (const string& frame : framesToSend) {
            if (!connectionHandler.sendFrameAscii(frame, '\0')) {
                cout << "Could not send message to server." << endl;
                break;
            }
        }
        
        if (!protocol.getIsConnected() && line != "login") {
             // Logic to handle disconnect state if needed
        }
    }

    if (socketThread.joinable()) {
        socketThread.join();
    }
    return 0;
}