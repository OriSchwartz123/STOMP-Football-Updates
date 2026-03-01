#include "../include/StompProtocol.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <map>

using namespace std;

StompProtocol::StompProtocol() : 
    isConnected(false), 
    subscriptionIdCounter(0), 
    receiptIdCounter(0), 
    currentUsername(""), 
    topicToSubId(), 
    gameUpdates() 
{}

vector<string> split(const string &s, char delimiter) {
    vector<string> tokens;
    string token;
    istringstream tokenStream(s);
    while (getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

Event parseEventBody(string body) {
    string team_a_name, team_b_name, event_name, description;
    int time = 0;
    std::map<std::string, std::string> game_updates;
    std::map<std::string, std::string> team_a_updates;
    std::map<std::string, std::string> team_b_updates;

    stringstream ss(body);
    string line;
    string currentSection = "";

    while (getline(ss, line)) {
        if (line.find("team a:") == 0) team_a_name = line.substr(7);
        else if (line.find("team b:") == 0) team_b_name = line.substr(7);
        else if (line.find("event name:") == 0) event_name = line.substr(11);
        else if (line.find("time:") == 0) time = stoi(line.substr(5));
        else if (line == "general game updates:") currentSection = "general";
        else if (line == "team a updates:") currentSection = "team_a";
        else if (line == "team b updates:") currentSection = "team_b";
        else if (line == "description:") currentSection = "description";
        else {
            if (currentSection == "description") {
                description += line + "\n";
            } else if (line.find(':') != string::npos) {
                int split = line.find(':');
                string key = line.substr(0, split);
                string val = line.substr(split + 1);
                if (currentSection == "general") game_updates[key] = val;
                else if (currentSection == "team_a") team_a_updates[key] = val;
                else if (currentSection == "team_b") team_b_updates[key] = val;
            }
        }
    }
    

    Event e(team_a_name, team_b_name, event_name, time, game_updates, team_a_updates, team_b_updates, description);
    return e;
}


vector<string> StompProtocol::processKeyboardCommand(string line) {
    vector<string> result;
    vector<string> args = split(line, ' ');
    if (args.empty()) return result;

    string command = args[0];

    // 1. LOGIN
    if (command == "login") {
        if (isConnected) {
            cout << "The client is already logged in, log out before trying again" << endl;
            return result;
        }
        if (args.size() < 4) {
            cout << "Usage: login {host:port} {username} {password}" << endl;
            return result;
        }
        string username = args[2];
        string password = args[3];
        currentUsername = username;

        string frame = "CONNECT\n"
                       "accept-version:1.2\n"
                       "host:stomp.cs.bgu.ac.il\n"
                       "login:" + username + "\n"
                       "passcode:" + password + "\n"
                       "\n";
        result.push_back(frame);
    }

    // 2. JOIN
    else if (command == "join") {
        if (!isConnected) { cout << "Error: Not logged in" << endl; return result; }
        if (args.size() < 2) return result;
        string gameName = args[1];
        int subId = subscriptionIdCounter++;
        int receiptId = receiptIdCounter++;
        topicToSubId[gameName] = subId;
        string frame = "SUBSCRIBE\n"
                       "destination:/" + gameName + "\n"
                       "id:" + to_string(subId) + "\n"
                       "receipt:" + to_string(receiptId) + "\n"
                       "\n";
        result.push_back(frame);
    }

    // 3. EXIT
    else if (command == "exit") {
        if (!isConnected) { cout << "Error: Not logged in" << endl; return result; }
        string gameName = args[1];
        if (topicToSubId.find(gameName) == topicToSubId.end()) {
            cout << "Error: User is not subscribed to channel " << gameName << endl;
            return result;
        }
        int subId = topicToSubId[gameName];
        topicToSubId.erase(gameName);
        int receiptId = receiptIdCounter++;
        string frame = "UNSUBSCRIBE\n"
                       "id:" + to_string(subId) + "\n"
                       "receipt:" + to_string(receiptId) + "\n"
                       "\n";
        result.push_back(frame);
    }

    // 4. REPORT
    else if (command == "report") {
        if (!isConnected) { cout << "Error: Not logged in" << endl; return result; }
        string jsonPath = args[1];
        names_and_events parsedData;
        try {
            parsedData = parseEventsFile(jsonPath);
        } catch (...) {
            cout << "Error parsing file" << endl;
            return result;
        }

        string gameName = parsedData.team_a_name + "_" + parsedData.team_b_name;

        for (const Event& event : parsedData.events) {
            gameUpdates[gameName][currentUsername].push_back(event);

            string body = "user:" + currentUsername + "\n" +
                          "team a:" + parsedData.team_a_name + "\n" +
                          "team b:" + parsedData.team_b_name + "\n" +
                          "event name:" + event.get_name() + "\n" +
                          "time:" + to_string(event.get_time()) + "\n" +
                          "general game updates:\n";
            for (auto const& pair : event.get_game_updates()) body += pair.first + ":" + pair.second + "\n";
            body += "team a updates:\n";
            for (auto const& pair : event.get_team_a_updates()) body += pair.first + ":" + pair.second + "\n";
            body += "team b updates:\n";
            for (auto const& pair : event.get_team_b_updates()) body += pair.first + ":" + pair.second + "\n";
            body += "description:\n" + event.get_discription();

            string frame = "SEND\n"
                           "destination:/" + gameName + "\n"
                           "\n" + 
                           body + "\n";
            result.push_back(frame);
        }
    }

    else if (command == "summary") {
        if (args.size() < 4) {
            cout << "Usage: summary {game_name} {user} {file}" << endl;
            return result;
        }
        string gameName = args[1];
        string user = args[2];
        string filePath = args[3];

        if (gameUpdates.find(gameName) == gameUpdates.end() || 
            gameUpdates[gameName].find(user) == gameUpdates[gameName].end()) {
            cout << "No updates found for user " << user << " in game " << gameName << endl;
            return result;
        }

        const vector<Event>& events = gameUpdates[gameName][user];
        if (events.empty()) return result;

        map<string, string> general_stats;
        map<string, string> team_a_stats;
        map<string, string> team_b_stats;

        for (const auto& event : events) {
            for (auto const& pair : event.get_game_updates()) general_stats[pair.first] = pair.second;
            for (auto const& pair : event.get_team_a_updates()) team_a_stats[pair.first] = pair.second;
            for (auto const& pair : event.get_team_b_updates()) team_b_stats[pair.first] = pair.second;
        }

        ofstream outFile(filePath);
        if (!outFile.is_open()) {
            cout << "Error opening file: " << filePath << endl;
            return result;
        }

        size_t underscore = gameName.find('_');
        string teamA = gameName.substr(0, underscore);
        string teamB = gameName.substr(underscore + 1);
        outFile << teamA << " vs " << teamB << "\n";

        outFile << "Game stats:\n";
        outFile << "General stats:\n";
        for (auto const& pair : general_stats) outFile << pair.first << ":" << pair.second << "\n";
        
        outFile << teamA << " stats:\n";
        for (auto const& pair : team_a_stats) outFile << pair.first << ":" << pair.second << "\n";
        
        outFile << teamB << " stats:\n";
        for (auto const& pair : team_b_stats) outFile << pair.first << ":" << pair.second << "\n";

        outFile << "Game event reports:\n";
        for (const auto& event : events) {
            outFile << event.get_time() << " - " << event.get_name() << ":\n\n";
            outFile << event.get_discription() << "\n\n\n";
        }

        outFile.close();
        cout << "Summary written to " << filePath << endl;
    }

    else if (command == "logout") {
        if (!isConnected) { cout << "Error: Not logged in" << endl; return result; }
        int receiptId = receiptIdCounter++;
        string frame = "DISCONNECT\n"
                       "receipt:" + to_string(receiptId) + "\n"
                       "\n";
        result.push_back(frame);
    }

    return result;
}


bool StompProtocol::processServerFrame(string frame) {
    stringstream ss(frame);
    string command;
    getline(ss, command);

    if (command == "MESSAGE") {
        string line;
        string destination;
        string user;
        string body;
        bool inBody = false;
        
        while(getline(ss, line)) {
            if (line.empty() && !inBody) { inBody = true; continue; }
            if (!inBody) {
                if (line.find("destination:") == 0) destination = line.substr(12);
            } else {
                body += line + "\n";
                if (line.find("user:") == 0) user = line.substr(5);
            }
        }
        if (!body.empty() && body.back() == '\0') body.pop_back();

        cout << "Received update from server on " << destination << ":" << endl;
        cout << body << endl;

        if (!destination.empty() && !user.empty()) {
            try {
                Event receivedEvent = parseEventBody(body);
                gameUpdates[destination][user].push_back(receivedEvent);
            } catch (...) {
                cout << "Error parsing incoming message body" << endl;
            }
        }
    }
    else if (command == "CONNECTED") {
        isConnected = true;
        cout << "Login successful" << endl;
    }
    else if (command == "RECEIPT") {
        string line; 
        string receiptId;
        while(getline(ss, line)) {
             if (line.find("receipt-id:") == 0) receiptId = line.substr(11);
        }
        cout << "Receipt received " << receiptId << endl;
    }
    else if (command == "ERROR") {
        cout << "Error frame received:" << endl;
        cout << frame << endl;
        isConnected = false;
        return false;
    }

    return true;
}

void StompProtocol::setIsConnected(bool status) { isConnected = status; }
bool StompProtocol::getIsConnected() const { return isConnected; }