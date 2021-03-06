----------------------------- MODULE load_balancer -----------------------------
(***************************************************************************)
(* Specifies a simple load balancer.                                       *)
(***************************************************************************)

\* Extends some built-in TLA+ modules
EXTENDS Naturals, Sequences, TLC

\* The `TCPChannel` mapping macro used in this specification is parameterized
\* by a `BUFFER_SIZE` constant. This value controls the number of messages being
\* held in a buffer by each process. Processes trying to send a message to another
\* process with a full buffer wil "block" (not be scheduled by TLC).
CONSTANT BUFFER_SIZE

\* Define a constant identifier for the load balancer.
CONSTANT LoadBalancerId

\* The number of servers and clients in the model checking setup.
CONSTANTS NUM_SERVERS, NUM_CLIENTS

\* TLC should assume that both numbers are greater than zero (i.e., we always
\* have at least one server and one client). Note, however, that increasing
\* these numbers makes the number of states to be checked by TLC to grow
\* exponentially.
ASSUME NUM_SERVERS > 0 /\ NUM_CLIENTS > 0

\* GET_PAGE is a label attached to messages sent from the clients to
\* the load balancer.
CONSTANTS GET_PAGE

\* Represents a file that can be returned by the server
CONSTANT WEB_PAGE

(***************************************************************************
--mpcal LoadBalancer {
  define {
    \* total nodes in the system:
    \*    number of clients + number of servers + the load balancer
    NUM_NODES == NUM_CLIENTS + NUM_SERVERS + 1
  }

  \* The TCPChannel mapping macro models a communication channel
  \* between two processes using TCP-like semantics. In particular:
  \*
  \* - reading from the channel "blocks" unless there is a message
  \*   ready to be read.
  \* - message delivery is reliable and ordered (i.e., FIFO).
  mapping macro TCPChannel {
      read {
          await Len($variable) > 0;
          with (msg = Head($variable)) {
              $variable := Tail($variable);
              yield msg;
          };
      }

      write {
          await Len($variable) < BUFFER_SIZE;
          yield Append($variable, $value);
      }
  }

  \* Mapping macros keep implementation-specific behavior that we don't
  \* want to model check outside of our archetype definitions.
  \* In the case of this load balancer, how a server retrieves a web page
  \* is orthogonal to the correctness of the properties we are interested
  \* to check with this specification.
  \*
  \* This mapping macro abstracts the process of reading a web page by
  \* always returning the `WEB_PAGE` constant when the variable is read.
  \*
  \* Since "writing" to this mapping is meaningless, the attempting to write
  \* to a variable mapped with WebPage will result in a model checking
  \* error (see `assert(FALSE)` in the write definition).
  mapping macro WebPages {
     read {
         yield WEB_PAGE;
     }

     write {
         assert(FALSE);
         yield $value;
     }
  }

  \* ALoadBalancer is the archetype that defines the behavior of
  \* the load balancer process. The `mailboxes` parameter represents
  \* connections to all nodes in the system.
  archetype ALoadBalancer(ref mailboxes)

  \* Local variables of this archetype:
  variables
             \* Holds messages received by the load balancer (sent
             \* by clients)
             msg,

             \* identifier attached to every message sent to servers by
             \* the load balancer.
             next = 0;
  {
      main:
        while (TRUE) {

            \* waits for a message to be received. Upon receipt, the `assert`
            \* call ensures that the message is of type `GET_PAGE`, the only
            \* type of message supported in this simple specification.
            \*
            \* Every message received by the load balancer is expected to
            \* be a record of the following type.
            \*
            \*    [message_type : Int, client_id : Int, path: Interface{}]
            \*
            \* Note that tuples are 1-indexed.
            rcvMsg:
              msg := mailboxes[LoadBalancerId];
              assert(msg.message_type = GET_PAGE);

            \* the load balancer needs to forward the client request to the
            \* server, who will process the request and send a web page back to
            \* the client.
            \*
            \* The message sent to the server is a tuple in the format:
            \*
            \*     [message_id : Int, client_id : Int, path: Interface{}]
            \*
            \* We send the client ID here so that the server can directly
            \* reply to a client, bypassing the load balancer. This is usually
            \* not what happens in practice, but the model is simple
            \* enough for our (illustrative) purposes.
            sendServer:
              next := (next % NUM_SERVERS) + 1;
              mailboxes[next] := [message_id |-> next, client_id |-> msg.client_id, path |-> msg.path];
        }
  }

  \* AServer is the archetype that defines the behavior of the servers
  \* in our system. The two parameters it recieves are:
  \*
  \* - mailboxes: contains connections to every node in the system
  \* - file_system: abstraction of a real file system. In practice,
  \*                this is implementation specific and irrelevant for
  \*                the properties we want to check in this specification
  archetype AServer(ref mailboxes, file_system)

  \* Local variables
  variable
            \* temporary buffer to hold incoming messages
            msg;
  {
      serverLoop:
        while (TRUE) {

            \* waits for an incoming message. Note that the only process
            \* that sends messages to the server is the load balancer process
            \* (defined according to the ALoadBalancer archetype) and the
            \* message has the type [message_id : Int, client_id : Int]
            rcvReq:
              msg := mailboxes[self];

          sendPage:
            \* sends a web page (read from `page_stream`) back to the requester
            \* i.e., the client.
            mailboxes[msg.client_id] := file_system[msg.path];
        }
  }

  \* Client processes are given integer identifiers starting from NUM_SERVERS+1.
  \* Keep in mind that this "range" notation in PlusCal defines a set, and is
  \* _inclusive_ (i.e., NUM_SERVERS+NUM_CLIENTS+1 is part of the set).
  \*
  \* The parameters received by a client are:
  \*
  \* - mailboxes: contains connections to every node in the system
  \* - instream: a stream of inputs to the client
  \* - outstream: an output stream, where the client sends the messages it receives
  \*              from servers.
  archetype AClient(ref mailboxes, instream, ref outstream)

  \* Local variables
  variable
            \* Temporary buffers to hold messages
            req, resp;
  {
      clientLoop:
        while (TRUE) {

            \* First, the client makes a request to the load balancer.
            \* The format of the message is a tuple:
            \*
            \*     [message_type : Int, client_id : Int, path : Interface{}].
            \*
            \* If you check the ALoadBalancer definition, this is the message format
            \* expected there.
            \*
            \* Remember that `self` is an implicitly defined, immutable variable that
            \* contains the process identifier of the "running" process.
            clientRequest:
              req := [message_type |-> GET_PAGE, client_id |-> self, path |-> instream];
              mailboxes[LoadBalancerId] := req;

            \* Clients then wait for the response to the previously sent request.
            \* Since there is only one type of web page in this simple specification
            \* (defined by the WEB_PAGE constant), we assert here that the message
            \* received indeed is equal our expected web page.
            clientReceive:
              resp := mailboxes[self];
              outstream := resp;
        }
  }

  \* GLOBAL VARIABLES *\

  variables
             \* our network is modeled as a function from node identifier
             \* to a sequence of incoming messages
             network = [id \in 0..(NUM_NODES-1) |-> <<>>],

             \* set as input and output "streams"
             in = 0, out = 0,
             
             fs = [f \in {in} |-> WEB_PAGE];

  \* PROCESS INSTANTIATION *\

  \* The system has a single load balancer entity, instantiated from the ALoadBalancer
  \* archetype. The model of our network is going to be the one defined by the TCPChannel
  \* mapping macro in all instantiations.
  fair process (LoadBalancer = LoadBalancerId) == instance ALoadBalancer(ref network)
      mapping network[_] via TCPChannel;

  \* Instantiate `NUM_SERVERS` server processes according to the AServer archetype.
  \* We map the page stream according to the WebPages mapping macro since this is
  \* an implementation detail that needs to be specified during implementation at
  \* a later stage.
  fair process (Servers \in 1..NUM_SERVERS) == instance AServer(ref network, fs)
      mapping network[_] via TCPChannel
      mapping fs[_] via WebPages;

  fair process (Client \in (NUM_SERVERS+1)..(NUM_SERVERS+NUM_CLIENTS)) == instance AClient(ref network, in, ref out)
      mapping network[_] via TCPChannel;
}

\* BEGIN PLUSCAL TRANSLATION
--algorithm LoadBalancer {
    variables network = [id \in (0)..((NUM_NODES)-(1)) |-> <<>>], in = 0, out = 0, fs = [f \in {in} |-> WEB_PAGE];
    define {
        NUM_NODES == ((NUM_CLIENTS)+(NUM_SERVERS))+(1)}
    fair process (LoadBalancer = LoadBalancerId)
    variables msg, next = 0, mailboxesRead, mailboxesWrite, mailboxesWrite0;
    {
        main:
            if (TRUE) {
                rcvMsg:
                    await (Len(network[LoadBalancerId]))>(0);
                    with (msg0 = Head(network[LoadBalancerId])) {
                        mailboxesWrite := [network EXCEPT ![LoadBalancerId] = Tail(network[LoadBalancerId])];
                        mailboxesRead := msg0;
                    };
                    msg := mailboxesRead;
                    assert ((msg).message_type)=(GET_PAGE);
                    network := mailboxesWrite;
                
                sendServer:
                    next := ((next)%(NUM_SERVERS))+(1);
                    await (Len(network[next]))<(BUFFER_SIZE);
                    mailboxesWrite := [network EXCEPT ![next] = Append(network[next], [message_id |-> next, client_id |-> (msg).client_id, path |-> (msg).path])];
                    network := mailboxesWrite;
                    goto main;
            
            } else {
                mailboxesWrite0 := network;
                network := mailboxesWrite0;
            };
    
    }
    fair process (Servers \in (1)..(NUM_SERVERS))
    variables msg, mailboxesRead0, mailboxesWrite1, file_systemRead, mailboxesWrite2;
    {
        serverLoop:
            if (TRUE) {
                rcvReq:
                    await (Len(network[self]))>(0);
                    with (msg1 = Head(network[self])) {
                        mailboxesWrite1 := [network EXCEPT ![self] = Tail(network[self])];
                        mailboxesRead0 := msg1;
                    };
                    msg := mailboxesRead0;
                    network := mailboxesWrite1;
                
                sendPage:
                    file_systemRead := WEB_PAGE;
                    await (Len(network[(msg).client_id]))<(BUFFER_SIZE);
                    mailboxesWrite1 := [network EXCEPT ![(msg).client_id] = Append(network[(msg).client_id], file_systemRead)];
                    network := mailboxesWrite1;
                    goto serverLoop;
            
            } else {
                mailboxesWrite2 := network;
                network := mailboxesWrite2;
            };
    
    }
    fair process (Client \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)))
    variables req, resp, instreamRead, mailboxesWrite3, mailboxesRead1, outstreamWrite, mailboxesWrite4, outstreamWrite0;
    {
        clientLoop:
            if (TRUE) {
                clientRequest:
                    instreamRead := in;
                    req := [message_type |-> GET_PAGE, client_id |-> self, path |-> instreamRead];
                    await (Len(network[LoadBalancerId]))<(BUFFER_SIZE);
                    mailboxesWrite3 := [network EXCEPT ![LoadBalancerId] = Append(network[LoadBalancerId], req)];
                    network := mailboxesWrite3;
                
                clientReceive:
                    await (Len(network[self]))>(0);
                    with (msg2 = Head(network[self])) {
                        mailboxesWrite3 := [network EXCEPT ![self] = Tail(network[self])];
                        mailboxesRead1 := msg2;
                    };
                    resp := mailboxesRead1;
                    outstreamWrite := resp;
                    network := mailboxesWrite3;
                    out := outstreamWrite;
                    goto clientLoop;
            
            } else {
                mailboxesWrite4 := network;
                outstreamWrite0 := out;
                network := mailboxesWrite4;
                out := outstreamWrite0;
            };
    
    }
}
\* END PLUSCAL TRANSLATION



***************************************************************************)
\* BEGIN TRANSLATION
\* Process variable msg of process LoadBalancer at line 249 col 15 changed to msg_
CONSTANT defaultInitValue
VARIABLES network, in, out, fs, pc

(* define statement *)
NUM_NODES == ((NUM_CLIENTS)+(NUM_SERVERS))+(1)

VARIABLES msg_, next, mailboxesRead, mailboxesWrite, mailboxesWrite0, msg, 
          mailboxesRead0, mailboxesWrite1, file_systemRead, mailboxesWrite2, 
          req, resp, instreamRead, mailboxesWrite3, mailboxesRead1, 
          outstreamWrite, mailboxesWrite4, outstreamWrite0

vars == << network, in, out, fs, pc, msg_, next, mailboxesRead, 
           mailboxesWrite, mailboxesWrite0, msg, mailboxesRead0, 
           mailboxesWrite1, file_systemRead, mailboxesWrite2, req, resp, 
           instreamRead, mailboxesWrite3, mailboxesRead1, outstreamWrite, 
           mailboxesWrite4, outstreamWrite0 >>

ProcSet == {LoadBalancerId} \cup ((1)..(NUM_SERVERS)) \cup (((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)))

Init == (* Global variables *)
        /\ network = [id \in (0)..((NUM_NODES)-(1)) |-> <<>>]
        /\ in = 0
        /\ out = 0
        /\ fs = [f \in {in} |-> WEB_PAGE]
        (* Process LoadBalancer *)
        /\ msg_ = defaultInitValue
        /\ next = 0
        /\ mailboxesRead = defaultInitValue
        /\ mailboxesWrite = defaultInitValue
        /\ mailboxesWrite0 = defaultInitValue
        (* Process Servers *)
        /\ msg = [self \in (1)..(NUM_SERVERS) |-> defaultInitValue]
        /\ mailboxesRead0 = [self \in (1)..(NUM_SERVERS) |-> defaultInitValue]
        /\ mailboxesWrite1 = [self \in (1)..(NUM_SERVERS) |-> defaultInitValue]
        /\ file_systemRead = [self \in (1)..(NUM_SERVERS) |-> defaultInitValue]
        /\ mailboxesWrite2 = [self \in (1)..(NUM_SERVERS) |-> defaultInitValue]
        (* Process Client *)
        /\ req = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ resp = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ instreamRead = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ mailboxesWrite3 = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ mailboxesRead1 = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ outstreamWrite = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ mailboxesWrite4 = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ outstreamWrite0 = [self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) |-> defaultInitValue]
        /\ pc = [self \in ProcSet |-> CASE self = LoadBalancerId -> "main"
                                        [] self \in (1)..(NUM_SERVERS) -> "serverLoop"
                                        [] self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) -> "clientLoop"]

main == /\ pc[LoadBalancerId] = "main"
        /\ IF TRUE
              THEN /\ pc' = [pc EXCEPT ![LoadBalancerId] = "rcvMsg"]
                   /\ UNCHANGED << network, mailboxesWrite0 >>
              ELSE /\ mailboxesWrite0' = network
                   /\ network' = mailboxesWrite0'
                   /\ pc' = [pc EXCEPT ![LoadBalancerId] = "Done"]
        /\ UNCHANGED << in, out, fs, msg_, next, mailboxesRead, mailboxesWrite, 
                        msg, mailboxesRead0, mailboxesWrite1, file_systemRead, 
                        mailboxesWrite2, req, resp, instreamRead, 
                        mailboxesWrite3, mailboxesRead1, outstreamWrite, 
                        mailboxesWrite4, outstreamWrite0 >>

rcvMsg == /\ pc[LoadBalancerId] = "rcvMsg"
          /\ (Len(network[LoadBalancerId]))>(0)
          /\ LET msg0 == Head(network[LoadBalancerId]) IN
               /\ mailboxesWrite' = [network EXCEPT ![LoadBalancerId] = Tail(network[LoadBalancerId])]
               /\ mailboxesRead' = msg0
          /\ msg_' = mailboxesRead'
          /\ Assert(((msg_').message_type)=(GET_PAGE), 
                    "Failure of assertion at line 260, column 21.")
          /\ network' = mailboxesWrite'
          /\ pc' = [pc EXCEPT ![LoadBalancerId] = "sendServer"]
          /\ UNCHANGED << in, out, fs, next, mailboxesWrite0, msg, 
                          mailboxesRead0, mailboxesWrite1, file_systemRead, 
                          mailboxesWrite2, req, resp, instreamRead, 
                          mailboxesWrite3, mailboxesRead1, outstreamWrite, 
                          mailboxesWrite4, outstreamWrite0 >>

sendServer == /\ pc[LoadBalancerId] = "sendServer"
              /\ next' = ((next)%(NUM_SERVERS))+(1)
              /\ (Len(network[next']))<(BUFFER_SIZE)
              /\ mailboxesWrite' = [network EXCEPT ![next'] = Append(network[next'], [message_id |-> next', client_id |-> (msg_).client_id, path |-> (msg_).path])]
              /\ network' = mailboxesWrite'
              /\ pc' = [pc EXCEPT ![LoadBalancerId] = "main"]
              /\ UNCHANGED << in, out, fs, msg_, mailboxesRead, 
                              mailboxesWrite0, msg, mailboxesRead0, 
                              mailboxesWrite1, file_systemRead, 
                              mailboxesWrite2, req, resp, instreamRead, 
                              mailboxesWrite3, mailboxesRead1, outstreamWrite, 
                              mailboxesWrite4, outstreamWrite0 >>

LoadBalancer == main \/ rcvMsg \/ sendServer

serverLoop(self) == /\ pc[self] = "serverLoop"
                    /\ IF TRUE
                          THEN /\ pc' = [pc EXCEPT ![self] = "rcvReq"]
                               /\ UNCHANGED << network, mailboxesWrite2 >>
                          ELSE /\ mailboxesWrite2' = [mailboxesWrite2 EXCEPT ![self] = network]
                               /\ network' = mailboxesWrite2'[self]
                               /\ pc' = [pc EXCEPT ![self] = "Done"]
                    /\ UNCHANGED << in, out, fs, msg_, next, mailboxesRead, 
                                    mailboxesWrite, mailboxesWrite0, msg, 
                                    mailboxesRead0, mailboxesWrite1, 
                                    file_systemRead, req, resp, instreamRead, 
                                    mailboxesWrite3, mailboxesRead1, 
                                    outstreamWrite, mailboxesWrite4, 
                                    outstreamWrite0 >>

rcvReq(self) == /\ pc[self] = "rcvReq"
                /\ (Len(network[self]))>(0)
                /\ LET msg1 == Head(network[self]) IN
                     /\ mailboxesWrite1' = [mailboxesWrite1 EXCEPT ![self] = [network EXCEPT ![self] = Tail(network[self])]]
                     /\ mailboxesRead0' = [mailboxesRead0 EXCEPT ![self] = msg1]
                /\ msg' = [msg EXCEPT ![self] = mailboxesRead0'[self]]
                /\ network' = mailboxesWrite1'[self]
                /\ pc' = [pc EXCEPT ![self] = "sendPage"]
                /\ UNCHANGED << in, out, fs, msg_, next, mailboxesRead, 
                                mailboxesWrite, mailboxesWrite0, 
                                file_systemRead, mailboxesWrite2, req, resp, 
                                instreamRead, mailboxesWrite3, mailboxesRead1, 
                                outstreamWrite, mailboxesWrite4, 
                                outstreamWrite0 >>

sendPage(self) == /\ pc[self] = "sendPage"
                  /\ file_systemRead' = [file_systemRead EXCEPT ![self] = WEB_PAGE]
                  /\ (Len(network[(msg[self]).client_id]))<(BUFFER_SIZE)
                  /\ mailboxesWrite1' = [mailboxesWrite1 EXCEPT ![self] = [network EXCEPT ![(msg[self]).client_id] = Append(network[(msg[self]).client_id], file_systemRead'[self])]]
                  /\ network' = mailboxesWrite1'[self]
                  /\ pc' = [pc EXCEPT ![self] = "serverLoop"]
                  /\ UNCHANGED << in, out, fs, msg_, next, mailboxesRead, 
                                  mailboxesWrite, mailboxesWrite0, msg, 
                                  mailboxesRead0, mailboxesWrite2, req, resp, 
                                  instreamRead, mailboxesWrite3, 
                                  mailboxesRead1, outstreamWrite, 
                                  mailboxesWrite4, outstreamWrite0 >>

Servers(self) == serverLoop(self) \/ rcvReq(self) \/ sendPage(self)

clientLoop(self) == /\ pc[self] = "clientLoop"
                    /\ IF TRUE
                          THEN /\ pc' = [pc EXCEPT ![self] = "clientRequest"]
                               /\ UNCHANGED << network, out, mailboxesWrite4, 
                                               outstreamWrite0 >>
                          ELSE /\ mailboxesWrite4' = [mailboxesWrite4 EXCEPT ![self] = network]
                               /\ outstreamWrite0' = [outstreamWrite0 EXCEPT ![self] = out]
                               /\ network' = mailboxesWrite4'[self]
                               /\ out' = outstreamWrite0'[self]
                               /\ pc' = [pc EXCEPT ![self] = "Done"]
                    /\ UNCHANGED << in, fs, msg_, next, mailboxesRead, 
                                    mailboxesWrite, mailboxesWrite0, msg, 
                                    mailboxesRead0, mailboxesWrite1, 
                                    file_systemRead, mailboxesWrite2, req, 
                                    resp, instreamRead, mailboxesWrite3, 
                                    mailboxesRead1, outstreamWrite >>

clientRequest(self) == /\ pc[self] = "clientRequest"
                       /\ instreamRead' = [instreamRead EXCEPT ![self] = in]
                       /\ req' = [req EXCEPT ![self] = [message_type |-> GET_PAGE, client_id |-> self, path |-> instreamRead'[self]]]
                       /\ (Len(network[LoadBalancerId]))<(BUFFER_SIZE)
                       /\ mailboxesWrite3' = [mailboxesWrite3 EXCEPT ![self] = [network EXCEPT ![LoadBalancerId] = Append(network[LoadBalancerId], req'[self])]]
                       /\ network' = mailboxesWrite3'[self]
                       /\ pc' = [pc EXCEPT ![self] = "clientReceive"]
                       /\ UNCHANGED << in, out, fs, msg_, next, mailboxesRead, 
                                       mailboxesWrite, mailboxesWrite0, msg, 
                                       mailboxesRead0, mailboxesWrite1, 
                                       file_systemRead, mailboxesWrite2, resp, 
                                       mailboxesRead1, outstreamWrite, 
                                       mailboxesWrite4, outstreamWrite0 >>

clientReceive(self) == /\ pc[self] = "clientReceive"
                       /\ (Len(network[self]))>(0)
                       /\ LET msg2 == Head(network[self]) IN
                            /\ mailboxesWrite3' = [mailboxesWrite3 EXCEPT ![self] = [network EXCEPT ![self] = Tail(network[self])]]
                            /\ mailboxesRead1' = [mailboxesRead1 EXCEPT ![self] = msg2]
                       /\ resp' = [resp EXCEPT ![self] = mailboxesRead1'[self]]
                       /\ outstreamWrite' = [outstreamWrite EXCEPT ![self] = resp'[self]]
                       /\ network' = mailboxesWrite3'[self]
                       /\ out' = outstreamWrite'[self]
                       /\ pc' = [pc EXCEPT ![self] = "clientLoop"]
                       /\ UNCHANGED << in, fs, msg_, next, mailboxesRead, 
                                       mailboxesWrite, mailboxesWrite0, msg, 
                                       mailboxesRead0, mailboxesWrite1, 
                                       file_systemRead, mailboxesWrite2, req, 
                                       instreamRead, mailboxesWrite4, 
                                       outstreamWrite0 >>

Client(self) == clientLoop(self) \/ clientRequest(self)
                   \/ clientReceive(self)

Next == LoadBalancer
           \/ (\E self \in (1)..(NUM_SERVERS): Servers(self))
           \/ (\E self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)): Client(self))
           \/ (* Disjunct to prevent deadlock on termination *)
              ((\A self \in ProcSet: pc[self] = "Done") /\ UNCHANGED vars)

Spec == /\ Init /\ [][Next]_vars
        /\ WF_vars(LoadBalancer)
        /\ \A self \in (1)..(NUM_SERVERS) : WF_vars(Servers(self))
        /\ \A self \in ((NUM_SERVERS)+(1))..((NUM_SERVERS)+(NUM_CLIENTS)) : WF_vars(Client(self))

Termination == <>(\A self \in ProcSet: pc[self] = "Done")

\* END TRANSLATION


(* INVARIANTS *)


\* This is an _invariant_ of our specification: in other words,
\* we expect the BuffersOk predicate to always be true in every step of execution
BufferOk(node) == Len(network[node]) >= 0 /\ Len(network[node]) <= BUFFER_SIZE
BuffersOk == \A node \in DOMAIN network : BufferOk(node)


(* PROPERTIES *)

\* This is a property we would like to check about our specification.
\* Properties are defined using _temporal logic_. In this specific example,
\* we want to make sure that every client that requests a web page (i.e., are
\* in the 'clientRequest' label) eventually receive a response (i.e., are
\* in the 'clientReceive' label). In order to specify this property, we have to
\* write the formula as if the client enters 'clientReceive' label, it will
\* eventually successfully receive a response and then go back to issuing
\* another request in the 'clientRequest' label.
ReceivesPage(client) == pc[client] = "clientReceive" ~> pc[client] = "clientRequest"
ClientsOk == \A client \in (NUM_SERVERS+1)..(NUM_SERVERS+NUM_CLIENTS) : ReceivesPage(client)

=============================================================================
\* Modification History
\* Last modified Wed Feb 27 14:42:14 PST 2019 by rmc
\* Last modified Mon Feb 25 14:25:46 PST 2019 by minh