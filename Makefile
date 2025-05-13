.PHONY: all run clean

all: run

run:
	javac ircBot.java && java ircBot

clean:
	rm -f *.class
