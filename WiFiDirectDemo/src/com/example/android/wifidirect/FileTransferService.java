// Copyright 2011 Google Inc. All Rights Reserved.

package com.example.android.wifidirect;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

import com.example.android.wifidirect.DeviceDetailFragment.Connexion;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            //String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            //Socket socket = new Socket();
           // int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
               // socket.bind(null);
                //socket.connect((new InetSocketAddress(host, 8987)), SOCKET_TIMEOUT);

                //Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
               // OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();                
                
                ArrayList<Client> list= ((ClientLists) getApplicationContext()).getClients();
                int i=0;
                for(i=0; i<list.size();i++){
                	new Thread( new SendFile(list.get(i),cr,fileUri) ).start();
                }
                
                //DeviceDetailFragment.copyFile(is, stream);
                //Log.d(WiFiDirectActivity.TAG, "Client: Data written");
           

        }
    }
    
    class SendFile implements Runnable
	{
		//private Socket socket;
		private Client client;
		private InputStream is=null;
		private long filesize;
		public SendFile( Client client, ContentResolver cr, String fileUri)
		{
			//this.socket=new Socket();
			this.client=client;
            try {
            	
                is = cr.openInputStream(Uri.parse(fileUri));
                try {
					filesize=(long)is.available();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            } catch (FileNotFoundException e) {
                Log.d(WiFiDirectActivity.TAG, e.toString());
            }
			
		}

		@Override
		public void run() {
			Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
			//socket.bind(null);
			//socket.connect((new InetSocketAddress(address, 8988)), SOCKET_TIMEOUT);
			Log.d(WiFiDirectActivity.TAG, "Server socket - sending file...");
			Socket socket = client.getSocket();
			OutputStream stream=null;
			try {
				stream = socket.getOutputStream();
				//PrintWriter msgStream= new PrintWriter(stream);
				byte[] buffOut =new String("WAITFORFILE").getBytes();
				stream.write(buffOut);
				
				Log.d("",new String(buffOut));
				SystemClock.sleep(1000);
				if(DeviceDetailFragment.copyFile(is, stream)){
					is.close();
					
					Log.d(WiFiDirectActivity.TAG, "Server: Data written"); 
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				Log.d(WiFiDirectActivity.TAG, e.toString());
			}
			//while(true);
		}
		
	}
}


