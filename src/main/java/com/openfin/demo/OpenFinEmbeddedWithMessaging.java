package com.openfin.demo;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.json.JSONObject;

import com.openfin.desktop.Ack;
import com.openfin.desktop.AckListener;
import com.openfin.desktop.Application;
import com.openfin.desktop.ApplicationOptions;
import com.openfin.desktop.DesktopConnection;
import com.openfin.desktop.DesktopException;
import com.openfin.desktop.DesktopStateListener;
import com.openfin.desktop.RuntimeConfiguration;
import com.openfin.desktop.Window;
import com.openfin.desktop.WindowOptions;
import com.sun.jna.Native;

public class OpenFinEmbeddedWithMessaging extends JFrame implements DesktopStateListener {
	
	private final static String APP_UUID = "OpenFinSimpleGUI";
	private final static String IAB_TOPIC = "IAB_ECHO";
	private final static String CHANNEL_TOPIC = "CHANNEL_ECHO";
	
	private DesktopConnection openFinConnection;
	private CompletableFuture<String> iabEchoResponseFuture;
	private CompletableFuture<String> channelEchoResponseFuture;
	private JPanel glasspane;
	private Canvas openfinPane;
	private Application jsApplication;
	
	public OpenFinEmbeddedWithMessaging() {
		super("OpenFin Embedded with IAB and Channel API Demo");
		this.launchOpenFin();
		this.initGUI();
	}
	
	private void launchOpenFin() {
		//use a new thread to launch openfin process
		Runnable r = ()->{
			try {
				this.openFinConnection = new DesktopConnection(UUID.randomUUID().toString());
				RuntimeConfiguration config = new RuntimeConfiguration();
				config.setRuntimeVersion("stable");
				openFinConnection.connect(config, this, 60);
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		};
		
		Thread t = new Thread(r);
		t.start();
	}
	
	private void startJsApplication() {
		try {
			//copy JsApp.html to temp directory
			InputStream isJsApp = this.getClass().getResourceAsStream("/JsApp.html");
			Path targetFile = Files.createTempFile("OpenFinJava_", ".html");
			FileOutputStream os = new FileOutputStream(targetFile.toFile());
			FileChannel outputChannel = os.getChannel();
			ReadableByteChannel inputChannel = Channels.newChannel(isJsApp);
			outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
			outputChannel.close();
			//start application using this html file.
			ApplicationOptions appOpts = new ApplicationOptions(APP_UUID, APP_UUID, targetFile.toUri().toString());
			WindowOptions winOpts = new WindowOptions();
			winOpts.setFrame(false);
			winOpts.setResizable(false);
			winOpts.setResizeRegionSize(0);
			winOpts.setResizeRegionBottomRightCorner(0);
			appOpts.setMainWindowOptions(winOpts);
			Application.createApplication(appOpts, this.openFinConnection).thenCompose(app->{
				this.jsApplication = app;
				return app.runAsync().thenAccept(ofApp ->{
					this.embedOpenFinWindow();
				});
			}).exceptionally(e->{
				e.printStackTrace();
				return null;
			});
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			
		}
	}
	
	private void initGUI() {
		//init glass pane
		this.glasspane = new JPanel(new BorderLayout());
		JLabel lbl = new JLabel("Loading, please wait......");
		lbl.setHorizontalAlignment(SwingConstants.CENTER);
		glasspane.add(lbl, BorderLayout.CENTER);
		this.setGlassPane(this.glasspane);
		
		//init content pane.
		JPanel pnl = new JPanel(new BorderLayout());
		pnl.setPreferredSize(new Dimension(640, 480));
		pnl.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20), BorderFactory.createTitledBorder("OpenFin Java Adapter Demo")));
		
		JPanel pnlButton = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton btnIab = new JButton("Send IAB Message");
		btnIab.addActionListener(ae->{
			btnIab.setEnabled(false);
			// this is a blocking call, so we'll want to run it in a worker thread
			new SwingWorker<String, Void>() {
				@Override
				protected String doInBackground() throws Exception {
					return sendIabEcho("HoHoHo");
				}

				@Override
				protected void done() {
					btnIab.setEnabled(true);
				}
				
			}.execute();
		});
		JButton btnChannel = new JButton("Send Channel Message");
		btnChannel.addActionListener(ae->{
			btnChannel.setEnabled(false);
			// this is a blocking call, so we'll want to run it in a worker thread
			new SwingWorker<String, Void>() {
				@Override
				protected String doInBackground() throws Exception {
					return sendChannelEcho("OhOhOh");
				}

				@Override
				protected void done() {
					btnChannel.setEnabled(true);
				}
				
			}.execute();
		});
		pnlButton.add(btnIab);
		pnlButton.add(btnChannel);
		
		this.openfinPane = new Canvas();
		
		pnl.add(pnlButton, BorderLayout.NORTH);
		pnl.add(this.openfinPane, BorderLayout.CENTER);
		
		this.getContentPane().add(pnl, BorderLayout.CENTER);
		this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (jsApplication != null ){
					try {
						OpenFinEmbeddedWithMessaging.this.setVisible(false);
						OpenFinEmbeddedWithMessaging.this.jsApplication.close();
						OpenFinEmbeddedWithMessaging.this.openFinConnection.disconnect();
					}
					catch (DesktopException e1) {
						e1.printStackTrace();
					}
				}
				else {
					System.exit(0);
				}
			}
		});
		this.pack();
		this.setLocationRelativeTo(null);
		this.setVisible(true);
		this.glasspane.setVisible(true);
	}
	
	private void embedOpenFinWindow() {
		Window openFinWin = Window.wrap(APP_UUID, APP_UUID, this.openFinConnection);
		openFinWin.embedInto(Native.getComponentID(this.openfinPane), 
				this.openfinPane.getWidth(), this.openfinPane.getHeight(), 
				new AckListener() {
						@Override
						public void onSuccess(Ack ack) {
							//resize embedded OpenFin window when container panel is resized
							SwingUtilities.invokeLater(()->{
								OpenFinEmbeddedWithMessaging.this.openfinPane.addComponentListener(new ComponentAdapter() {
									@Override
									public void componentResized(ComponentEvent e) {
										openFinWin.setBounds(0, 0, e.getComponent().getWidth(), e.getComponent().getHeight(), null);
									}
								});
								//hide glasspane
								OpenFinEmbeddedWithMessaging.this.glasspane.setVisible(false);
							});
						}
	
						@Override
						public void onError(Ack ack) {
						}
				});
	}
	
	private void iabEchoListener(String sourceUuid, String topic, Object payload) {
		try {
			this.iabEchoResponseFuture = new CompletableFuture<>();
			System.out.println("received from IAB: " + payload.toString());
			openFinConnection.getInterApplicationBus().unsubscribe(APP_UUID, IAB_TOPIC, this::iabEchoListener);
			this.iabEchoResponseFuture.complete(payload.toString());
		}
		catch (DesktopException e) {
			e.printStackTrace();
		}
	}

	/**
	 * sends a message to the OpenFin application using IAB and wait for the response.
	 * @param message
	 * @return
	 */
	private String sendIabEcho(String message) {
		try {
			openFinConnection.getInterApplicationBus().subscribe(APP_UUID, IAB_TOPIC, this::iabEchoListener);
			openFinConnection.getInterApplicationBus().send(APP_UUID, IAB_TOPIC, message);
		}
		catch (DesktopException e) {
			e.printStackTrace();
		}
		finally {
			
		}
		try {
			return iabEchoResponseFuture.get(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * sends a message to the OpenFin application using Channel API and wait for the response. 
	 * @param message
	 */
	private String sendChannelEcho(String message) {
		try {
			this.channelEchoResponseFuture = new CompletableFuture<>();
			openFinConnection.getChannel("SIMPLE_GUI_CHANNEL").connectAsync().thenAccept(channelClient->{
				channelClient.dispatchAsync(CHANNEL_TOPIC, message).thenAccept(ack->{
					String result = ((JSONObject)ack.getData()).getString("result");
					System.out.println("Channel result: " + result);
					this.channelEchoResponseFuture.complete(result);
				});
			});
			return channelEchoResponseFuture.get(5, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public void onReady() {
		//openfin ready
		System.out.println("openfin ready......");
		//don't block the thread in callback.
		CompletableFuture.runAsync(()->{
			this.startJsApplication();;
		});
	}

	@Override
	public void onClose(String error) {
		System.exit(0);
	}

	@Override
	public void onError(String reason) {
	}

	@Override
	public void onMessage(String message) {
	}

	@Override
	public void onOutgoingMessage(String message) {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(()->{
			new OpenFinEmbeddedWithMessaging();
		});
	}

}
