package com.example.android.wifidirect;

import java.net.InetAddress;
import java.net.Socket;

public class Client {
	private InetAddress address;
	private Socket socket;
    
	public Client(InetAddress address,Socket socket ){
		this.address=address;
		this.socket=socket;
	}
	
	public InetAddress getAddress(){
		return address;
	}
	
	public Socket getSocket(){
		return socket;
	}
}
