#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <string>
#include <vector>
#include <map>
#include <mutex>

using namespace std;

class StompProtocol {
private:
    bool isConnected;
    int subscriptionIdCounter;
    int receiptIdCounter;
    string currentUsername;

    map<string, int> topicToSubId;

    map<string, map<string, vector<Event>>> gameUpdates;

public:
    StompProtocol();

    vector<string> processKeyboardCommand(string line);

    bool processServerFrame(string frame);

    bool getIsConnected() const;
    void setIsConnected(bool status);
};