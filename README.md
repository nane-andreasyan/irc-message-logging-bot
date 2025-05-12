## IRCLogBotAUA

IRCLogBotAUA is a Java-based IRC bot that logs all messages from IRC channels it joins. The bot responds to user requests for retrieving message history prior to a specific mention and supports concurrent private message handling.


### Usage

1. Compile and Run the Bot (using Makefile):

To compile and run the IRC bot, simply use:

```
make
```
This will automatically compile `ircBot.java` and run it in one step. To clean up compiled files:
```
make clean
```

The bot connects using static identity values:

Nickname: `IRCLogBotAUA`

Username: `IRCLogBotAUA`

Real name: `IRCLogBotAUA`

2. Invite the bot to a channel using a valid INVITE command:

```
/invite IRCLogBotAUA
```

3. In the channel, mention the bot with its nickname (IRCLogBotAUA) to trigger log retrieval.

4. The bot will privately ask how many messages you want. Respond in private with a number. If given number of messages are not available the bot will warn the user.

### Log Format

Logs are stored in the `logs/` directory. The directory is created on local machine if it is not available.  
Each channel has its own log file named `log_<channelName>.txt.` 

Each message is timestamped and written in the format:

```
[yyyy-MM-ddTHH:mm:ss.SSSSSS] <username>: message
```

### CTCP Support

The bot replies to VERSION requests with:

```
VERSION IRCLogBot 1.0 Java
```

### Testing

This bot was tested using the `irssi` IRC client. The following commands are for Unix OS.

1. Install `irssi` IRC client using homebrew:

```
brew install irssi
```

2. Connect `irssi` IRC client:

```
irssi
/connect irc.oftc.net
```

3. Join any channel and invite IRCLogBotAUA bot:

```
/join #channelName
/invite IRCLogBotAUA
```

4. Mention the bot with its nickname to trigger log retrieval
```
IRCLogBotAUA
```

5. In other window via private message IRCLogBotAUA will ask how many messages you want. In order to view private messages do:

```
/window 3
```
To return to channel window do:
```
/window 2
```

### Dependencies

- Java 8 or higher
- Access to IRC `irc.oftc.net` server

### Notes

1. Messages containing the bot's nickname are not logged to avoid echoing its own interactions.

2. Bot nickname, username, and real name are hardcoded and not configurable at runtime.