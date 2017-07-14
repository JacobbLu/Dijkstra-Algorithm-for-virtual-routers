import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;

/////////////////////////////////////////////////////////////////////
public class router {
	public static final int NBR_ROUTER = 5;

	private static int id;				//args[0]
	private static InetAddress Addr;	//args[1]
	private static int H_Port;			//args[2]
	private static int R_Port;			//args[3]

	private static DatagramSocket socket;
	private static PrintWriter Write_to_log;

	private static circuit_DB links;//NBR_ROUTER
	private static int links_to[];
	private static link_cost[][] M;
	private static ArrayList<pkt_LSPDU> pkt_LSPDUs;
	private static int distance_table[];
	////////////////////////////////////////////
	public static void main(String[] args) throws Exception {
		///////initial
		if(args.length != 4){
			System.out.println("Invalid command line input");
        	System.exit(1);
		}
		id = Integer.parseInt(args[0]);
		Addr = InetAddress.getByName(args[1]);
		H_Port = Integer.parseInt(args[2]);
		R_Port = Integer.parseInt(args[3]);

		socket = new DatagramSocket();
		Write_to_log = new PrintWriter(new FileWriter(String.format("router%d.log", id)), true);
		pkt_LSPDUs = new ArrayList<pkt_LSPDU>();

		links_to = new int[NBR_ROUTER];
		distance_table = new int[NBR_ROUTER];
		M = new link_cost[NBR_ROUTER][NBR_ROUTER];
		for (int i=0; i < NBR_ROUTER; i++) {
	    	for (int j=0; j < NBR_ROUTER; j++) {
	    		if (i == j){
	    			M[i][i] = new link_cost(-1, 0);
	    			distance_table[i] = 0;
	    		}else{
	    			M[i][j] = new link_cost(-1, 2147483647);
	    			distance_table[i] = 2147483647;
	    		}
	    	}
	    }

		///////send pkt_INIT
		pkt_INIT INIT_pkt = new pkt_INIT(id);
		DatagramPacket INIT_pkt_data = new DatagramPacket(INIT_pkt.getByte(), 4, Addr, H_Port);
		socket.send(INIT_pkt_data);
		Write_to_log.printf("R%d sends an INIT: router_id %d\n", id, INIT_pkt.router_id);
		Write_to_log.flush();

		///////receive circuit_DB from the nse
		byte[] circuit_DB_data = new byte[1024];
		DatagramPacket circuit_DB_pkt = new DatagramPacket(circuit_DB_data, circuit_DB_data.length);
		socket.receive(circuit_DB_pkt);
		links = circuit_DB.parse(circuit_DB_pkt.getData());
		for(int i = 0; i < links.nbr_link; i++){
			pkt_LSPDU LSPDU_pkt = new pkt_LSPDU(id, links.linkcost[i].link, links.linkcost[i].cost);
			pkt_LSPDUs.add(LSPDU_pkt);
		}
		Write_to_log.printf("R%d receives circuit_DB: nbr_link %d\n", id, links.nbr_link);
		Write_to_log.flush();

		////////Write topology database to log file 
		Write_to_log.printf("# Topology database\n");
		Write_to_log.flush();
		for (int i = 1; i <= NBR_ROUTER; i++){
			ArrayList<pkt_LSPDU> temp = new ArrayList<pkt_LSPDU>();
			for (int j = 0; j < pkt_LSPDUs.size(); j++){
				if (pkt_LSPDUs.get(j).router_id == i){
					temp.add(pkt_LSPDUs.get(j));
				} 
			}
			Write_to_log.printf("R%d -> R%d nbr %d\n", id, i, temp.size());
			Write_to_log.flush();
			for (int j = 0; j < temp.size(); j++){
				Write_to_log.printf("R%d -> R%d link %d cost %d\n", id, i, temp.get(j).link_id, temp.get(j).cost);
			}
		}
		Write_to_log.flush();

		///////send the pkt_HELLOs
		for(int i = 0; i < links.nbr_link; i++){
			pkt_HELLO HELLO_pkt = new pkt_HELLO(id, links.linkcost[i].link);
			DatagramPacket pkt_HELLO_data = new DatagramPacket(HELLO_pkt.getByte(), 8, Addr, H_Port);
			socket.send(pkt_HELLO_data);
			Write_to_log.printf("R%d receives a HELLO: router_id %d link_id %d\n", id, HELLO_pkt.router_id, HELLO_pkt.link_id);
		}
		Write_to_log.flush();

		///////LOOP
		while(true){
			byte[] receive_data = new byte[1024];
			DatagramPacket receive_pkt = new DatagramPacket(receive_data, receive_data.length);
			socket.receive(receive_pkt);
			////////receive a pkt_HELLO
			if(receive_pkt.getLength() == 8){
				pkt_HELLO new_HELLO = pkt_HELLO.parse(receive_pkt.getData());
				int router_id = new_HELLO.router_id;
				int link_id = new_HELLO.link_id;
				for (int i = 0; i < pkt_LSPDUs.size(); i++){
					pkt_LSPDU LSPDU_pkt = pkt_LSPDUs.get(i);
					LSPDU_pkt.send_to(id, link_id);
					DatagramPacket LSPDU_pkt_data = new DatagramPacket(LSPDU_pkt.getByte(), 20, Addr, H_Port);
					socket.send(LSPDU_pkt_data);
					Write_to_log.printf("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n", id, LSPDU_pkt.sender, LSPDU_pkt.router_id, LSPDU_pkt.link_id, LSPDU_pkt.cost, LSPDU_pkt.via);
					Write_to_log.flush();
				}
				for (int i = 0; i < links.nbr_link ; i++){
					if(links.linkcost[i].link == link_id){
						links_to[i] = router_id;
					}
				}
				Write_to_log.printf("R%d receives a HELLO: router_id %d link_id %d\n", id, new_HELLO.router_id, new_HELLO.link_id);
			}
			////////receive a pkt_LSPDU
			else if (receive_pkt.getLength() == 20){
				pkt_LSPDU new_LSPDU = pkt_LSPDU.parse(receive_pkt.getData());
				boolean seen = false;
				for (int i = 0; i < pkt_LSPDUs.size(); i++){
					if(pkt_LSPDUs.get(i).link_id == new_LSPDU.link_id && pkt_LSPDUs.get(i).router_id == new_LSPDU.router_id){
						seen = true;
						break;
					}
				}
				if(!seen){
					boolean need_to_update = false;
					for(int i = 0; i < pkt_LSPDUs.size(); i++){
						if(new_LSPDU.link_id == pkt_LSPDUs.get(i).link_id){//means it is a connection
							need_to_update = true;
							M[new_LSPDU.router_id - 1][pkt_LSPDUs.get(i).router_id - 1] = new link_cost(new_LSPDU.link_id, new_LSPDU.cost);
							M[pkt_LSPDUs.get(i).router_id - 1][new_LSPDU.router_id - 1] = new link_cost(new_LSPDU.link_id, new_LSPDU.cost);
						}
					}
					if (need_to_update){
						//prepare
						ArrayList<Integer> used = new ArrayList<Integer>();
						for(int i = 0; i < NBR_ROUTER; i++){
							used.add(i);
							distance_table[i] = 2147483647;
							links_to[i] = i;
							if(i == id - 1){
								distance_table[i] = 0;
							}
						}
						//update
						while(used.size() > 0){
							int p = 0;
							int min = 2147483647;
							for (int i = 0; i < used.size(); i++){
								int r = used.get(i);
								if (min > distance_table[r]){
									p = i;
									min = distance_table[r];
								}
							}
							int r = used.get(p);
							used.remove(p);
							for (int i = 0; i < NBR_ROUTER; i++){
								if(M[i][r].cost < 2147483647 && M[i][r].cost + distance_table[r] < distance_table[i]){
									distance_table[i] = M[i][r].cost + distance_table[r];
									if(id != r + 1){
										links_to[i] = links_to[r];
									}
								}
							}
						}
						for(int i = 0; i < NBR_ROUTER; i++){
							links_to[i]++;
						}
					}
					pkt_LSPDUs.add(new_LSPDU);
					for (int i = 0; i < links.nbr_link; i++){
						if (links.linkcost[i].link != new_LSPDU.link_id){
							new_LSPDU.send_to(id, links.linkcost[i].link);
							DatagramPacket LSPDU_pkt_data = new DatagramPacket(new_LSPDU.getByte(), 20, Addr, H_Port);
							socket.send(LSPDU_pkt_data);
							Write_to_log.printf("R%d sends an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n", id, new_LSPDU.sender, new_LSPDU.router_id, new_LSPDU.link_id, new_LSPDU.cost, new_LSPDU.via);
							Write_to_log.flush();
						}
					}
					Write_to_log.printf("R%d receives an LS PDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n", id, new_LSPDU.sender, new_LSPDU.router_id, new_LSPDU.link_id, new_LSPDU.cost, new_LSPDU.via);
    				Write_to_log.flush();
    				///////////////////
    				Write_to_log.printf("# Topology database\n");
					Write_to_log.flush();
					for (int i = 1; i <= NBR_ROUTER; i++){
						ArrayList<pkt_LSPDU> temp = new ArrayList<pkt_LSPDU>();
						for (int j = 0; j < pkt_LSPDUs.size(); j++){
							if (pkt_LSPDUs.get(j).router_id == i){
								temp.add(pkt_LSPDUs.get(j));
							}
						}
						Write_to_log.printf("R%d -> R%d nbr %d\n", id, i, temp.size());
						Write_to_log.flush();
						for (int j = 0; j < temp.size(); j++){
							Write_to_log.printf("R%d -> R%d link %d cost %d\n", id, i, temp.get(j).link_id, temp.get(j).cost);
						}
					}
					Write_to_log.flush();
					//////////////////
					Write_to_log.printf("# RIB\n");
					Write_to_log.flush();
					for(int i = 0; i < NBR_ROUTER; i++){
						if (i == id - 1) {
							Write_to_log.printf("R%d -> R%d -> Local, 0\n", id, i + 1);
						}
						else if (distance_table[i] == 2147483647) {
							Write_to_log.printf("R%d -> R%d -> INF, INF\n", id, i + 1);
						}
						else {
							Write_to_log.printf("R%d -> R%d -> R%d, %d\n", id, i + 1, links_to[i], distance_table[i]);
						}
						Write_to_log.flush();
					}
				}
			}
		}
	}
}

///////////////////////////////////////////////////////////////////
class pkt_HELLO
{
	public int router_id;// id of the router who sends the HELLO PDU
	public int link_id;// id of the link through which it is sent

	public pkt_HELLO (int router_id, int link_id){
		this.router_id = router_id;
		this.link_id = link_id;
	}

	public byte[] getByte(){
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(router_id);
		buffer.putInt(link_id);
		return buffer.array();
	}

	public static pkt_HELLO parse(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		return new pkt_HELLO(router_id, link_id);
	}
}

///////////////////////////////////////////////////////////////////
class pkt_LSPDU 
{
	public int sender;//sender of the LS PDU
	public int router_id;//router id
	public int link_id;//link id
	public int cost;//cost of the link
	public int via;//id of the link through which the LS PDU is sent

	public pkt_LSPDU(int sender, int router_id, int link_id, int cost, int via){
		this.sender = sender;
		this.router_id = router_id;
		this.link_id = link_id;
		this.cost = cost;
		this.via = via;
	}

	public pkt_LSPDU(int router_id, int link_id, int cost) {
		this.router_id = router_id;
		this.link_id = link_id;
		this.cost = cost;
	}

	public void send_to(int sender, int via){
		this.sender = sender;
		this.via = via;
	}

	public byte[] getByte(){
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(sender);
		buffer.putInt(router_id);
		buffer.putInt(link_id);
		buffer.putInt(cost);
		buffer.putInt(via);
		return buffer.array();
	}

	public static pkt_LSPDU parse(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int sender = buffer.getInt();
		int router_id = buffer.getInt();
		int link_id = buffer.getInt();
		int cost = buffer.getInt();
		int via = buffer.getInt();
		return new pkt_LSPDU(sender, router_id, link_id, cost, via);
	}
}

////////////////////////////////////////////////////////////////////
class pkt_INIT
{
	public int router_id;

	public pkt_INIT(int router_id){
		this.router_id = router_id;//id of the router that send the INIT PDU
	}

	public byte[] getByte(){
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(router_id);
		return buffer.array();
	}

	public static pkt_INIT parse(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int router_id = buffer.getInt();
		return new pkt_INIT(router_id);
	}
}

///////////////////////////////////////////////////////////////////
class link_cost
{
	public int link;//link id
	public int cost;//associated cost

	public link_cost(int link, int cost){
		this.link = link;
		this.cost = cost;
	}
}

////////////////////////////////////////////////////////////////////
class circuit_DB
{
	public int nbr_link;//number of links attached to a router
	public link_cost[] linkcost;//we assume that at most NBR_ROUTER links are attached to each router

	public circuit_DB(int nbr_link, link_cost[] linkcost){
		this.nbr_link = nbr_link;
		this.linkcost = linkcost;
	}

	public static circuit_DB parse(byte[] UDPdata) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int nbr_link = buffer.getInt();
		link_cost[] linkcost = new link_cost[nbr_link];
		for (int i = 0; i < nbr_link; i++){
			linkcost[i] = new link_cost(buffer.getInt(), buffer.getInt());
		}
		return new circuit_DB(nbr_link, linkcost);
	}
}
