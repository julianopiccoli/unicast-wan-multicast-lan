                       +-----------+
              +--------|   Relay   |<------------+
              |        +-----------+             |
              |                                  |
    +---------|------+    +----------------------|----------------------+
    | LAN 2   v      |    |                      |                      |
    |     +--------+ |    | +--------+      +--------+       +--------+ |
    |     | User 5 | |    | | User 4 |      | User 1 |<------| User 2 | |
    |     +--------+ |    | +--------+      +--------+       +--------+ |
    |         |      |    |      ^                                |     |
    |         |      |    |      |                                |     |
    |         |      |    |      +--------------------------------+     |
    |         v      |    |                                       |     |
    |     +--------+ |    |                 +--------+            |     |
    |     | User 6 | |    |                 | User 3 |<-----------+     |
    |     +--------+ |    | LAN 1           +--------+                  |
    +----------------+    +---------------------------------------------+
    
The goal of this project is to create a framework for streaming data using a combination of multicast and unicast communication schemes. Multicast is used for communication between hosts located in the same Local Area Network (LAN). Unicast is only used for communicating with a relay server which provides a way for exchanging data between different "multicast clusters". This approach helps saving bandwidth and lowering latencies.

In each multicast cluster, one host acts like a "hub": it receives streams from the relay server and redistributes them locally to the others hosts using multicast. It also redirects streams originating from any other cluster member to the relay server. The hub definition is processed automatically each time a significant change occurs in the cluster (at startup when the cluster is formed and when the current "hub" leaves the cluster for example).

The image above shows an example of such a network. Two different LANs join the same conference session and one of the members starts transmitting data (User 2). Since it is not the hub for this cluster, it transmits using multicast. Host "User 1", which is the hub for this cluster, retransmits the stream it receives from User 2 to the relay using an unicast channel. The relay then redirects this stream to the hub of the cluster located at "LAN 2", which is "User 5". In its turn, it retransmits this stream to the other host of the cluster (User 6).

P.S.: I wrote this code back in 2008 as the final project for my undergraduate course. It surely needs some refactoring and updates.
