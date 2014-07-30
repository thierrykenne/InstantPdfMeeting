package com.example.android.wifidirect;

import java.util.ArrayList;

import android.app.Application;

public class ClientLists extends Application {
	private ArrayList<Client> clients= new ArrayList<Client>() ;
	
    
	public ArrayList<Client> getClients(){
		return clients;
		
	}
	
	public void addClient(Client client){
		this.clients.add(client);
	}
}


