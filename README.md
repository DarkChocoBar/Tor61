# Tor61
I'm working in the branch "DevelopTorRouter"

TorMain.java is what needs to be run.
I created a run script that runs TorMain.java. It's in the src directory

Tor61ProxyThread should probably be an inner class of Tor61ProxyServer, but I left it outside so ProxyServer wouldn't become one super huge class.
Same for P1PMessage. It should probably be an innerclass of RegisterationAgent since no one else ever uses it, but I left it separate for now so we could make changes easier/ we don't crowd these classes.
