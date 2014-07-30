/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

	protected static final int CHOOSE_FILE_RESULT_CODE = 20;
	private View mContentView = null;
	private WifiP2pDevice device;
	private WifiP2pInfo info;
	ProgressDialog progressDialog = null;
	private Socket clientSocketIp=null;
	ServerSocket serverSocketIp=null;
	private static final Object clientsListLock = new Object();
	private static final int ACK_IP = 10;
	private static final int SHOW_FIELD = 11;
	private static final int START_PDF_ACTIVITY=12;
    
	//show file chooser form on received IP from clients
	private Handler mIncomingHandler = null;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		//handle thread messages to the UI thread
		 mIncomingHandler=new myHandler(getActivity());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		mContentView = inflater.inflate(R.layout.device_detail, null);
		mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				WifiP2pConfig config = new WifiP2pConfig();
				config.deviceAddress = device.deviceAddress;
				config.wps.setup = WpsInfo.PBC;
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
						"Connecting to :" + device.deviceAddress, true, true
						//                        new DialogInterface.OnCancelListener() {
						//
						//                            @Override
						//                            public void onCancel(DialogInterface dialog) {
						//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
						//                            }
						//                        }
						);
				((DeviceActionListener) getActivity()).connect(config);

			}
		});

		mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						((DeviceActionListener) getActivity()).disconnect();
					}
				});

		mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						// Allow user to pick an image from Gallery or other
						// registered apps
						Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
						intent.setType("image/*");
						Log.d("start","start activity");
						startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
						
					}
				});

		mContentView.findViewById(R.id.btn_send_ip).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {			  
						try {
							
							OutputStream outStream = clientSocketIp.getOutputStream();
							byte[] buffOut=new String("BROFIST").getBytes();
							Log.d("",new String(buffOut));
							outStream.write(buffOut);;
							outStream.flush();
						} catch (IOException e) {
							Log.e("clientSocketIp", e.getMessage());
						}
					}
				});
		
		
		return mContentView;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		// User has picked an image. Transfer it to group owner i.e peer using
		// FileTransferService.
		Log.d("yes","yes msg send");
		Uri uri = data.getData();
		TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
		statusText.setText("Sending: " + uri);
		Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
		Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
		serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
		serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
		serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
				info.groupOwnerAddress.getHostAddress());
		serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
		getActivity().startService(serviceIntent);
	}

	@Override
	public void onConnectionInfoAvailable(final WifiP2pInfo info) {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		this.info = info;
		this.getView().setVisibility(View.VISIBLE);

		// The owner IP is now known.
		TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
		view.setText(getResources().getString(R.string.group_owner_text)
				+ ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
						: getResources().getString(R.string.no)));

		// InetAddress from WifiP2pInfo struct.
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

		// After the group negotiation, we assign the group owner as the file
		// server. The file server is single threaded, single connection server
		// socket.
		if (info.groupFormed && info.isGroupOwner) {
			//new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
			//.execute();
			new Thread(new ReceiveClientDataThread()).start();

		} else if (info.groupFormed) {
			// The other device acts as the client. In this case, we enable the
			// get file button.
			
			((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
					.getString(R.string.client_text));
			mContentView.findViewById(R.id.btn_send_ip).setVisibility(View.VISIBLE);

			new Thread(new ClientMsgThread(getActivity())).start();
			
			//new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
			//.execute();
		}

		// hide the connect button
		mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
	}

	/**
	 * Updates the UI with device data
	 * 
	 * @param device the device to be displayed
	 */
	public void showDetails(WifiP2pDevice device) {
		this.device = device;
		this.getView().setVisibility(View.VISIBLE);
		TextView view = (TextView) mContentView.findViewById(R.id.device_address);
		view.setText(device.deviceAddress);
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText(device.toString());

	}

	/**
	 * Clears the UI fields after a disconnect or direct mode disable operation.
	 */
	public void resetViews() {
		mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
		TextView view = (TextView) mContentView.findViewById(R.id.device_address);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.group_owner);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.status_text);
		view.setText(R.string.empty);
		mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
		this.getView().setVisibility(View.GONE);
	}

	/**
	 * A simple server socket that accepts connection and writes some data on
	 * the stream.
	 */
	public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

		private Context context;
		private TextView statusText;

		/**
		 * @param context
		 * @param statusText
		 */
		public FileServerAsyncTask(Context context, View statusText) {
			this.context = context;
			this.statusText = (TextView) statusText;
		}

		@Override
		protected String doInBackground(Void... params) {
			try {
				ServerSocket serverSocket = new ServerSocket(8987);
				Log.d(WiFiDirectActivity.TAG, "Client: Socket opened");
				Socket client = serverSocket.accept();
				Log.d(WiFiDirectActivity.TAG, "Client: connection done");
				final File f = new File(Environment.getExternalStorageDirectory() + "/"
						+ context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
						+ ".jpg");

				File dirs = new File(f.getParent());
				if (!dirs.exists())
					dirs.mkdirs();
				f.createNewFile();

				Log.d(WiFiDirectActivity.TAG, "Client: copying files " + f.toString());
				InputStream inputstream = client.getInputStream();
				copyFile(inputstream, new FileOutputStream(f));
				//serverSocket.close();
				return f.getAbsolutePath();
			} catch (IOException e) {
				Log.e(WiFiDirectActivity.TAG, e.getMessage());
				return null;
			}
		}

		/*
		 * (non-Javadoc)
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				statusText.setText("File copied - " + result);
				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.parse("file://" + result), "image/*");
				context.startActivity(intent);
			}

		}

		/*
		 * (non-Javadoc)
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			statusText.setText("Opening a Client socket");
		}

	}

	

	// client thread to send message to server

	class ClientMsgThread implements Runnable {
		private InputStream obInStream;
		private int nBytes;
		private Context context;
		
		public ClientMsgThread(Context context){
			this.context=context;
		}

		@Override
		public void run() {

			try {

				Log.d("yes3",info.groupOwnerAddress.getHostAddress());                
				clientSocketIp = new Socket(info.groupOwnerAddress.getHostAddress(), 8988);
				Log.d(WiFiDirectActivity.TAG, "Client socket - " + clientSocketIp.isConnected());
				//this.obInStream =new BufferedReader(new InputStreamReader(clientSocketIp.getInputStream()));
				this.obInStream =clientSocketIp.getInputStream();
				Boolean waitFile=false;
				while(true){
				        
						Log.d(WiFiDirectActivity.TAG, "prepare to receive data...");
						//receiving strings
						if(!waitFile){
							byte buffer[] = new byte[1024];
							nBytes = this.obInStream.read(buffer);
							Log.d(WiFiDirectActivity.TAG, "data received ..." + nBytes);
							String line= new String(buffer,0,nBytes);
							Log.d(WiFiDirectActivity.TAG, ""+ line);
							if (line.equals("ReceivedIP")) {
								Message msg=mIncomingHandler.obtainMessage();
								msg.what=ACK_IP;
								mIncomingHandler.sendMessage(msg);
							}
							if(line.equals("WAITFORFILE")){
								waitFile=true;
								Log.d(WiFiDirectActivity.TAG, "waiting for file ...");
							}
							
						}else{
							//receiving file
							waitFile=false;
														
							Log.d(WiFiDirectActivity.TAG, "Client: connection  for file done");
							final File f = new File(Environment.getExternalStorageDirectory() + "/"
									+ context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
									+ ".jpg");

							File dirs = new File(f.getParent());
							if (!dirs.exists())
								dirs.mkdirs();
							f.createNewFile();

							Log.d(WiFiDirectActivity.TAG, "Client: copying files " + f.toString());
							
							FileOutputStream fileStream=new  FileOutputStream(f);
							//copyFile(inputstream, fileStream);
							byte buf[] = new byte[1024];
							int len;
							try {
								while ((len = obInStream.read(buf)) != -1 ) {
									
									len = obInStream.read(buf);
									fileStream.write(buf, 0, len);
									Log.d("","writing data ...."+ len);
								}
								Log.d("","finish");
								//out.close();
								fileStream.close();
								
								
							} catch (IOException e) {
								Log.d(WiFiDirectActivity.TAG, e.toString());
				
							}
							Log.d(WiFiDirectActivity.TAG, "Client: Data written");							
					        
							//start the activity to manage the file
							Message msg = mIncomingHandler.obtainMessage();
							msg.what=START_PDF_ACTIVITY;
							msg.obj= f.getAbsolutePath();
							Log.d("","writing data ...."+f.getAbsolutePath());
							mIncomingHandler.sendMessage(msg);
							//break;
						}
				}

			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

		}

	}

	//receive ip address from clients
	class ReceiveClientDataThread implements Runnable {
		@Override
		public void run() {
			
			try {
				serverSocketIp = new ServerSocket(8988);
				serverSocketIp.setReuseAddress(true);
				//serverSocketIp.bind(null);
				Log.d(WiFiDirectActivity.TAG, "Server: SocketIP opened");
				while(true){
					Socket s = serverSocketIp.accept();
					Connexion c = new Connexion(s);
					Thread  conn_process = new Thread(c);
					conn_process.start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	// Server Thread to manage single client connection
	class Connexion implements Runnable
	{
		private Socket socket;
		private InputStream obInStream;
		private OutputStream obOutStream;
		public Connexion(Socket s)
		{
			this.socket=s;
			try{
				Log.d(WiFiDirectActivity.TAG, "Server: connection done");
				this.obInStream =this.socket.getInputStream();
				this.obOutStream= this.socket.getOutputStream();
				
			}catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			try
			{
				String line;
				while(true){
				
						Log.d(WiFiDirectActivity.TAG, "prepare to receive...");
						byte[] buff = new byte[1024];
						int i= obInStream.read(buff);
						
						Log.d("",new String(buff,0,i));
						line=new String(buff,0,i);
						if (line.equals("BROFIST")) {
							InetAddress clientIP=this.socket.getInetAddress();
							Log.d("clientip", "Client IP address: "+clientIP);
							
							byte[] buffOut=new String("ReceivedIP").getBytes();
							this.obOutStream.write(buffOut);;
							this.obOutStream.flush();
							
							//add client address in the list
							synchronized(clientsListLock){
								ArrayList<Client> list= ((ClientLists)getActivity().getApplication()).getClients();
								if(!list.contains(clientIP))
									((ClientLists)getActivity().getApplication()).addClient(new Client(clientIP,this.socket));	
							}
							Log.d("send","Send MSG2");
							//show file chooser button on GO
							Message msg = mIncomingHandler.obtainMessage();
							msg.what=SHOW_FIELD;
							mIncomingHandler.sendMessage(msg);
						}
				}
			}catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	
	
	public static boolean copyFile(InputStream inputStream, OutputStream out) {
		byte buf[] = new byte[1024];
		int len;
		try {
			while ((len = inputStream.read(buf)) != -1) {
				out.write(buf, 0, len);
                Log.d("","writing data ....");
			}
			Log.d("","finish");
			out.flush();
			//inputStream.close();
			
		} catch (IOException e) {
			Log.d(WiFiDirectActivity.TAG, e.toString());
			return false;
		}
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			// make sure you close the socket upon exiting
			if(serverSocketIp != null) serverSocketIp.close();
			if(serverSocketIp != null) clientSocketIp.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public class myHandler extends Handler{
		private WeakReference<Context> mContext;
		
		public myHandler(Context context){
			this.mContext=new WeakReference<Context>(context);
		}
		
		public void handleMessage(Message msg) {
	    	//server show form field and client hide send IP button
	    	Context context=mContext.get();
			
	    	if(msg.what==SHOW_FIELD)
	    		mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
	    	if(msg.what==ACK_IP){
	    		mContentView.findViewById(R.id.btn_send_ip).setVisibility(View.GONE);
	    		((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
						.getString(R.string.wait_file));
	    	}
	    	if(msg.what==START_PDF_ACTIVITY){
	    		Log.d("","starting handling file activity");
				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.parse("file://" + (String)msg.obj), "image/*");
				context.startActivity(intent);
	    		
	    	}
		}
	}

}
