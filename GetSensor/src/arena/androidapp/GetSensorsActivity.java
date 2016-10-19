package arena.androidapp;

import android.app.Activity;
import android.content.*;
import android.content.Intent;
import android.content.pm.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.View;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.GeomagneticField;
import android.util.*;
import android.net.wifi.*;
import android.content.BroadcastReceiver;
import android.location.*;
import android.app.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.net.*;

public class GetSensorsActivity extends Activity implements SensorEventListener {
	private static final String TAG = "GetSensorsActivity";
	private static boolean m_blnRecordStatus = false; // true: Recording; false: Stopped 
	private static long m_lSensorDataFileInterval = 0; /* 0: Always record in one file;
														 Other value: in milliseconds, split file accordingly
														 */
	
	private static final int DATA_TYPE_SENSOR = 1;
	
	/* Interval to record to a new data file */
	private String[] m_arrsFileIntervalOptions = {"None",
										"1 min", 
										"3 mins", 
										"5 mins",
										"10 mins",
										"20 mins",
										"30 mins",
										"1 hour",
										"3 hours",
										"5 hours",
										"12 hours",
										"24 hours"};
	private long[] m_arrlFileIntervalValues = {0,
									60*1000,
									180*1000,
									300*1000,
									600*1000,
									1200*1000,
									1800*1000,
									3600*1000,
									3600*3*1000,
									3600*5*1000,
									3600*12*1000,
									3600*24*1000};
	private SensorManager m_smPCO = null;
	private CheckBox m_chkOrient,m_chkGyro,m_chkAccl,m_chkMagnet,m_chkProximity,m_chkLight;
	private CheckBox m_chkRecCont;
	private RadioGroup m_rdgpSensor,m_rdgpSensorMode;
	private RadioButton m_rdSensorOrient,m_rdSensorGyro,m_rdSensorAccl,m_rdSensorMagnet,m_rdSensorProximity,m_rdSensorLight;
	private RadioButton m_rdSensorModeFastest,m_rdSensorModeGame,m_rdSensorModeNormal,m_rdSensorModeUI;
	private Spinner m_spnSelectFileInterval;
	
	private List<String> m_lstInterval = new ArrayList<String>();
	private ArrayAdapter<String> m_adpInterval;
	
	/* Sensor is available or not */
	private static boolean m_blnOrientPresent = false;
	private static boolean m_blnGyroPresent = false;
	private static boolean m_blnAcclPresent = false;
	private static boolean m_blnMagnetPresent = false;
	private static boolean m_blnProximityPresent = false;
	private static boolean m_blnLightPresent = false;
	

	/* Sensor is selected or not */
	private static boolean m_blnOrientEnabled = false; /* false: Sensor data will not be recorded; 
														true: will be recorded */
	private static boolean m_blnGyroEnabled = false;
	private static boolean m_blnAcclEnabled = false;
	private static boolean m_blnMagnetEnabled = false;
	private static boolean m_blnProximityEnabled = false;
	private static boolean m_blnLightEnabled = false;
	
	
	private static boolean m_blnGravityEnabled = false; //Don't Record gravity 
	//(A three dimensional vector indicating the direction and
	// magnitude of gravity)
	
	
	private static boolean m_blnRecordCont = false; //Record continue or not after interval
		
	/* Default delay mode for sensors */
	private static int m_iOrientMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iGyroMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iAcclMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iMagnetMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iProximityMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iLightMode = SensorManager.SENSOR_DELAY_NORMAL;
	private static int m_iGravityMode = SensorManager.SENSOR_DELAY_NORMAL;
	
	private Button m_btnRecord;
	private Button m_btnConnect;
	private EditText m_edtServerIP;
	private EditText m_edtServerPort;
	private TextView m_tvShowInfo;
	private TextView m_tvShowRecord;
	private String m_sRecordFile; //Sensor record file
	private String m_sFullPathFile; //Sensor record full pathfile
	private FileWriter m_fwSensorRecord = null;
	private GetSensorsActivity m_actHome = this;
	private Date m_dtFileStart;	//The time sensor data file is created (for each data file intervally)
	private ResolveInfo m_riHome;
		
	private float[] m_arrfAcclValues = null;	 // Store accelerometer values
	private float[] m_arrfMagnetValues = null; // Store magnetic values
	private float[] m_arrfOrientValues = new float[3]; // Store orientation values

	private AlarmReceiver m_receiverAlarm = null;
	
	static String m_sOrient = ",,,";
	static String m_sGyro = ",,,";
	static String m_sAccl = ",,,";
	static String m_sMagnet = ",,,";
	static String m_sProximity = ",";
	static String m_sLight = ",";
	
	static String m_sGravity = ",,,";
	
	static String m_sSensorAccuracy=",,";
	//private static boolean m_blnOrientValid = false;
	
	private static boolean m_blnConnected = false;
	private static int m_iLocalPort = 3456;
	private DatagramSocket m_cltSocket;
	private String m_sServerIP;
	private int m_iServerPort;
	private InetAddress m_serverAddress;
	
	
	private void stopRecord() {
		//Stop record. Close Sensor Record File
		if (m_fwSensorRecord != null) {
			try {
				m_fwSensorRecord.close();
				m_fwSensorRecord = null;
			} catch (IOException e) {
				//
			}
		}
				
		// Enable sensor setting when recording stops
		enableSensorSetting(true);
        m_tvShowInfo.setText(getString(R.string.defaultinfo));        			
        m_btnRecord.setText(getString(R.string.btn_start));
        m_tvShowRecord.setText("");
        resetValues();
		m_blnRecordStatus = false;
		
	}
	
	private void resetValues() {
		m_sOrient = ",,,";
		m_sGyro = ",,,";
		m_sAccl = ",,,";
		m_sMagnet = ",,,";
		m_sProximity = ",";
		m_sLight = ",";
	}
	
	
	private class AlarmReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			stopTimer();
			stopRecord();
		}
	}
	
	// Start a timer to stop recording when interval expires
	private void startTimer() {
		Intent intent;
		PendingIntent pIntent;
		AlarmManager am;
		Calendar cal;
		
		cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		cal.add(Calendar.SECOND, (int)(m_lSensorDataFileInterval/1000));
		
		intent = new Intent(getString(R.string.myalarm));
		pIntent = PendingIntent.getBroadcast(GetSensorsActivity.this,0,intent,PendingIntent.FLAG_ONE_SHOT); 
		am = (AlarmManager)getSystemService(Activity.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pIntent);
		
	}
	
	private void stopTimer() {
		Intent intent;
		PendingIntent pIntent;
		AlarmManager am;

		intent = new Intent(getString(R.string.myalarm));
		pIntent = PendingIntent.getBroadcast(GetSensorsActivity.this,0,intent,PendingIntent.FLAG_ONE_SHOT);
		am = (AlarmManager)getSystemService(Activity.ALARM_SERVICE);
		am.cancel(pIntent);
	}
		
	
	/* Set file identification and type 
	 * Add "_" + "O", "G", "A", "M", "P", "L" to represent each enabled sensor recorded in the data file
	 * Sensor data is recorded as "CSV" file 
	 */
	private String getFileType() {
		String sFileType = "";
		boolean blnHasUnderscore = false; //Indicate the first "_" before the letters representing sensors
		
		if (m_blnOrientEnabled) {
			sFileType = "_O";
			blnHasUnderscore = true;
		} 
		
		if (m_blnGyroEnabled) {
			if (blnHasUnderscore == true) {
				sFileType = sFileType + "G";
			} else {
				sFileType = "_G";
				blnHasUnderscore = true;
			}
		}
		
		if (m_blnAcclEnabled) {
			if (blnHasUnderscore == true) {
				sFileType = sFileType + "A";
			} else {
				sFileType = "_A";
				blnHasUnderscore = true;
			}
		}

		if (m_blnMagnetEnabled) {
			if (blnHasUnderscore == true) {
				sFileType = sFileType + "M";
			} else {
				sFileType = "_M";
				blnHasUnderscore = true;
			}
		}

		if (m_blnProximityEnabled) {
			if (blnHasUnderscore == true) {
				sFileType = sFileType + "P";
			} else {
				sFileType = "_P";
				blnHasUnderscore = true;
			}
		}

		if (m_blnLightEnabled) {
			if (blnHasUnderscore == true) {
				sFileType = sFileType + "L";
			} else {
				sFileType = "_L";
				blnHasUnderscore = true;
			}
		}
			
		sFileType = sFileType + ".csv";
		
		return sFileType;
		
	}
	
	/* Enable/Disable sensor setting 
	 * When recording is going on, sensor setting should be disabled 
	 */
	private void enableSensorSetting(boolean blnSettingEnabled) {
		if (m_blnOrientPresent) {
			m_chkOrient.setEnabled(blnSettingEnabled);
			m_rdSensorOrient.setEnabled(blnSettingEnabled);
		} else {
			m_chkOrient.setEnabled(false);
			m_rdSensorOrient.setEnabled(false);			
		}
		
		if (m_blnGyroPresent) {
			m_chkGyro.setEnabled(blnSettingEnabled);
			m_rdSensorGyro.setEnabled(blnSettingEnabled);
		} else {
			m_chkGyro.setEnabled(false);
			m_rdSensorGyro.setEnabled(false);
		}
		
		if (m_blnAcclPresent) {
			m_chkAccl.setEnabled(blnSettingEnabled);
			m_rdSensorAccl.setEnabled(blnSettingEnabled);
		} else {
			m_chkAccl.setEnabled(false);
			m_rdSensorAccl.setEnabled(false);
		}
		
		if (m_blnMagnetPresent) {
			m_chkMagnet.setEnabled(blnSettingEnabled);
			m_rdSensorMagnet.setEnabled(blnSettingEnabled);
		} else {
			m_chkMagnet.setEnabled(false);
			m_rdSensorMagnet.setEnabled(false);	
		}
		
		if (m_blnProximityPresent) {
			m_chkProximity.setEnabled(blnSettingEnabled);
			m_rdSensorProximity.setEnabled(blnSettingEnabled);
		} else {
			m_chkProximity.setEnabled(false);
			m_rdSensorProximity.setEnabled(false);
		}
		
		if (m_blnLightPresent){
			m_chkLight.setEnabled(blnSettingEnabled);
			m_rdSensorLight.setEnabled(blnSettingEnabled);
		} else {
			m_chkLight.setEnabled(false);
			m_rdSensorLight.setEnabled(false);			
		}

				
		if (m_blnOrientPresent || 
			m_blnGyroPresent ||
			m_blnAcclPresent ||
			m_blnMagnetPresent ||
			m_blnProximityPresent ||
			m_blnLightPresent) {
			m_rdSensorModeFastest.setEnabled(blnSettingEnabled);
			m_rdSensorModeGame.setEnabled(blnSettingEnabled);
			m_rdSensorModeNormal.setEnabled(blnSettingEnabled);
			m_rdSensorModeUI.setEnabled(blnSettingEnabled);
			m_spnSelectFileInterval.setEnabled(blnSettingEnabled);
			m_chkRecCont.setEnabled(blnSettingEnabled);
		} else {
			//No sensor is installed, disable mode selection
			m_rdSensorModeFastest.setEnabled(false);
			m_rdSensorModeGame.setEnabled(false);
			m_rdSensorModeNormal.setEnabled(false);
			m_rdSensorModeUI.setEnabled(false);
		}
		
		
	}


	/* Set default widget status and setting */
    private void setDefaultStatus() {
    	m_blnOrientEnabled = false;
    	m_blnGyroEnabled = false;
    	m_blnAcclEnabled = false;
    	m_blnMagnetEnabled = false;
    	m_blnProximityEnabled = false;
    	m_blnLightEnabled = false;
    	
    	
    	m_iOrientMode = SensorManager.SENSOR_DELAY_NORMAL;
    	m_iGyroMode = SensorManager.SENSOR_DELAY_NORMAL;
    	m_iAcclMode = SensorManager.SENSOR_DELAY_NORMAL;
    	m_iMagnetMode = SensorManager.SENSOR_DELAY_NORMAL;
    	m_iProximityMode = SensorManager.SENSOR_DELAY_NORMAL;
    	m_iLightMode = SensorManager.SENSOR_DELAY_NORMAL;
    	
    	m_chkOrient.setChecked(false);
    	m_chkGyro.setChecked(false);
    	m_chkAccl.setChecked(false);
    	m_chkMagnet.setChecked(false);
    	m_chkProximity.setChecked(false);
    	m_chkLight.setChecked(false);
    	    	
    	m_lSensorDataFileInterval = 0;
    	m_spnSelectFileInterval.setSelection(0);
    	m_tvShowInfo.setText(getString(R.string.defaultinfo));
    	m_tvShowRecord.setText("");
    	enableSensorSetting(true);
    	m_rdSensorOrient.setChecked(true);
    	m_rdSensorModeNormal.setChecked(true); 
    	m_blnRecordStatus = false;
    	m_btnRecord.setText(getString(R.string.btn_start));
    }
	

	private OnCheckedChangeListener m_chkRecContEnableListener = new OnCheckedChangeListener() {
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (m_chkRecCont.isChecked()) {
				m_blnRecordCont = true;
			} else {
				m_blnRecordCont = false;
			}
		}			
	};
    
    
    
	/* Event for sensor enable/disable */
	private OnCheckedChangeListener m_chkSensorEnableListener = new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			// TODO Auto-generated method stub
				if (m_chkOrient.isChecked()) {
					m_blnOrientEnabled = true;
				 	//m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ORIENTATION),m_iOrientMode);
					if (!m_blnAcclEnabled) {
						m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),m_iAcclMode);
					}
					if (!m_blnMagnetEnabled) {
						m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),m_iMagnetMode);
					}
				} else {
					m_blnOrientEnabled = false;
					//m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ORIENTATION));
					if (!m_blnAcclEnabled) {
						m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
					}
					if (!m_blnMagnetEnabled) {
						m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
					}
				}
				
				if (m_chkGyro.isChecked()) {
					m_blnGyroEnabled = true;
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_GYROSCOPE),m_iGyroMode);					
				} else {
					m_blnGyroEnabled = false;
					m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
				} 
				
				if (m_chkAccl.isChecked()) {
					m_blnAcclEnabled = true;
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),m_iAcclMode);
				} else {
					m_blnAcclEnabled = false;
					m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
				}
				
				if (m_chkMagnet.isChecked()) {
					m_blnMagnetEnabled = true;
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),m_iMagnetMode);					
				} else {
					m_blnMagnetEnabled = false;
					if (m_blnOrientEnabled == false) {
						m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));	
					}
				}

				if (m_chkProximity.isChecked()) {
					m_blnProximityEnabled = true;
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_PROXIMITY),m_iProximityMode);					
				} else {
					m_blnProximityEnabled = false;	
					m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_PROXIMITY));					
				}

				if (m_chkLight.isChecked()) {
					m_blnLightEnabled = true;
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LIGHT),m_iLightMode);					
				} else {
					m_blnLightEnabled = false;	
					m_smPCO.unregisterListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LIGHT));					
				}
				
				
				if (m_blnGravityEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_GRAVITY),m_iGravityMode);
				}
				
			}			
	};
	
	/* Event listener for record interval spinner */
    private Spinner.OnItemSelectedListener m_spnListener = new Spinner.OnItemSelectedListener() {
		public void onItemSelected(AdapterView arg0, View arg1, int arg2, long arg3) {
			// TODO Auto-generated method stub
			int nIndex;
			nIndex = Long.valueOf(m_adpInterval.getItemId(arg2)).intValue();
			// Set file interval for creating new data file
			m_lSensorDataFileInterval = m_arrlFileIntervalValues[nIndex];
		}
		
		public void onNothingSelected(AdapterView arg0) {
			// TODO Auto-generated method stub
		}
		
    };
    
    /* Event listener for sensor radiogroup selection */
    private RadioGroup.OnCheckedChangeListener m_rdgpSensorListener = new RadioGroup.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId) {
			
			// TODO Auto-generated method stub
			int iModeType = SensorManager.SENSOR_DELAY_NORMAL;
			//super.onCheckedChanged(group,checkedId);
			
			if (m_rdSensorOrient.isChecked()) {
				iModeType = m_iOrientMode;
			} else if (m_rdSensorGyro.isChecked()) {
				iModeType = m_iGyroMode;
			} else if (m_rdSensorAccl.isChecked()) {
				iModeType = m_iAcclMode;
			} else if (m_rdSensorMagnet.isChecked()) {
				iModeType = m_iMagnetMode;
			} else if (m_rdSensorProximity.isChecked()) {
				iModeType = m_iProximityMode;
			} else if (m_rdSensorLight.isChecked()) {
				iModeType = m_iLightMode;
			}
			
			if (iModeType == SensorManager.SENSOR_DELAY_FASTEST) {
				m_rdSensorModeFastest.setChecked(true);
			} else if (iModeType == SensorManager.SENSOR_DELAY_GAME) {
				m_rdSensorModeGame.setChecked(true);
			} else if (iModeType == SensorManager.SENSOR_DELAY_NORMAL) {
				m_rdSensorModeNormal.setChecked(true);
			} else if (iModeType == SensorManager.SENSOR_DELAY_UI) {
				m_rdSensorModeUI.setChecked(true);
			}

		}
    };
    
    /* Event listener for sensor mode radiogroup selection */
    private RadioGroup.OnCheckedChangeListener m_rdgpSensorModeListener = new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				
				// TODO Auto-generated method stub
				int iDelayMode = SensorManager.SENSOR_DELAY_NORMAL;
				
				if (m_rdSensorModeFastest.isChecked()) {
					iDelayMode = SensorManager.SENSOR_DELAY_FASTEST;
				} else if (m_rdSensorModeGame.isChecked()) {
					iDelayMode = SensorManager.SENSOR_DELAY_GAME;	
				} else if (m_rdSensorModeNormal.isChecked()) {
					iDelayMode = SensorManager.SENSOR_DELAY_NORMAL;
				} else if (m_rdSensorModeUI.isChecked()) {
					iDelayMode = SensorManager.SENSOR_DELAY_UI;
				}

				if (m_rdSensorOrient.isChecked()) {
					m_iOrientMode = iDelayMode;
				} else if (m_rdSensorGyro.isChecked()) {
					m_iGyroMode = iDelayMode;
				} else if (m_rdSensorAccl.isChecked()) {
					m_iAcclMode = iDelayMode;
				} else if (m_rdSensorMagnet.isChecked()){
					m_iMagnetMode = iDelayMode;
				} else if (m_rdSensorProximity.isChecked()) {
					m_iProximityMode = iDelayMode;
				} else if (m_rdSensorLight.isChecked()) {
					m_iLightMode = iDelayMode;
				}

				//Register sensors according to the enable/disable status
				if (m_blnOrientEnabled) {
					//m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ORIENTATION),m_iOrientMode);
					if (!m_blnAcclEnabled) { 
						m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),m_iAcclMode);
					}
					
					if (!m_blnMagnetEnabled) {
						m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),m_iMagnetMode);						
					}
				}
				
				if (m_blnGyroEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_GYROSCOPE),m_iGyroMode);
				}
				
				if (m_blnAcclEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),m_iAcclMode);
				}
				
				if (m_blnMagnetEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),m_iMagnetMode);
				}

				if (m_blnProximityEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_PROXIMITY),m_iProximityMode);
				}
				
				if (m_blnLightEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_LIGHT),m_iLightMode);
				}
				
				if (m_blnGravityEnabled) {
					m_smPCO.registerListener(m_actHome, m_smPCO.getDefaultSensor(Sensor.TYPE_GRAVITY),m_iGravityMode);
				}
			}
     };


     /* Event listener for record button (Start/Stop) */
     private Button.OnClickListener m_btnConnectListener = new Button.OnClickListener(){
     	public void onClick(View v) {
     		String sServerPort;
     		String sSendMsg;
     		
     		//If "Disconnect" is pressed, disconnect it
     		if (m_blnConnected == true) {
     			sSendMsg = "D";
				byte sndData[] = sSendMsg.getBytes();
				try {
					//Inform server: Disconnect
					DatagramPacket packet = new DatagramPacket(sndData, sndData.length, m_serverAddress, m_iServerPort);				
					m_cltSocket.send(packet);
				} catch (Exception e) {
					//
				}
     			
     			m_cltSocket.close();
     			m_blnConnected = false;
     			m_btnConnect.setText(getString(R.string.btn_connect));
     			return;
     		}
     		
     		//If "Connect" is pressed, connect to server
     		if ((m_edtServerIP.getText().length() == 0) || (m_edtServerPort.getText().length() == 0)) {
     			Toast.makeText(getApplicationContext(), getString(R.string.alert_msg), Toast.LENGTH_SHORT).show(); 
     			return;
     		}

     		m_sServerIP = m_edtServerIP.getText().toString();
     		sServerPort = m_edtServerPort.getText().toString();		
     		m_iServerPort = Integer.parseInt(sServerPort);
     		
     		try {
     			m_cltSocket = new DatagramSocket();
     			m_serverAddress = InetAddress.getByName(m_sServerIP);

     			sSendMsg = "C";
				byte sndData[] = sSendMsg.getBytes();
				try {
					//Inform server: Connect
					DatagramPacket packet = new DatagramPacket(sndData, sndData.length, m_serverAddress, m_iServerPort);				
					m_cltSocket.send(packet);
				} catch (Exception e) {
					//
				}
     			
     			m_blnConnected = true;
     		} catch (Exception e) {
     			m_blnConnected = false;
     			m_tvShowInfo.setText("Failed to connect with PC!");
     		}
     		
     		if (m_blnConnected == true) {
     			m_btnConnect.setText(getString(R.string.btn_disconnect));
     		}
     		
     	}
     };
     
     
     /* Event listener for record button (Start/Stop) */
     private Button.OnClickListener m_btnRecordListener = new Button.OnClickListener(){
     	public void onClick(View v) {
     		String sDataDir;
     		File flDataFolder;
     		boolean blnSensorSelected = false;
     		String sShowInfo = "";
     		
     		if (m_blnRecordStatus == false) {

     			if ((m_blnOrientEnabled == false) && 
     				(m_blnGyroEnabled == false) && 
     				(m_blnAcclEnabled == false) && 
     				(m_blnMagnetEnabled == false) &&
     				(m_blnProximityEnabled == false) &&
     				(m_blnLightEnabled == false)) {
     				// No sensor has been selected
     				blnSensorSelected = false;
     			} else {
     				blnSensorSelected = true;
     			}
     			
     			     			
     			if (blnSensorSelected == false) {
     				m_tvShowInfo.setText(getString(R.string.promptselect));
     				return;
     			}
     			
     			//Start to record
     			m_dtFileStart = new Date();
     	        final String DATE_FORMAT = "yyyyMMddHHmmss";
     			SimpleDateFormat spdCurrentTime = new SimpleDateFormat(DATE_FORMAT);
     			//m_sRecordFile = curDateTime.format(m_dtFileStart) + ".csv";
     			if (blnSensorSelected) {
     				m_sRecordFile = spdCurrentTime.format(m_dtFileStart);
     			}
     			     			        			
     			// Check whether SD Card has been plugged in
     			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
     				sDataDir = Environment.getExternalStorageDirectory().getAbsolutePath() + 
     										File.separator + getString(R.string.sensordata_folder);
     				flDataFolder = new File(sDataDir);
     				//Check whether /mnt/sdcard/SensorData/ exists
     				if (!flDataFolder.exists()) {
     					//Does not exist, create it
     					if (flDataFolder.mkdir()) {
     						if (blnSensorSelected) {
     							m_sFullPathFile = sDataDir + File.separator + m_sRecordFile;
     						}
     						     						
     					} else {
     						//Failed to create
     						m_tvShowInfo.setText("Failed to record Sensor data on SD Card!");
     						return;
     					}
     					
     				} else {
     					if (blnSensorSelected) {
     						m_sFullPathFile = sDataDir + File.separator + m_sRecordFile;
     					}
     					     					
     				} 
     			} else {        				
     				//NO SD Card
     				m_tvShowInfo.setText("Please insert SD Card!");
     				return;
     			}

     			if (blnSensorSelected) {
     				// Append file type information to the file name
     				m_sFullPathFile = m_sFullPathFile + getFileType();
     			
	     			try {
	     				m_fwSensorRecord = new FileWriter(m_sFullPathFile);
	      			} catch (IOException e) {
	      				m_tvShowInfo.setText("Failed to record Sensor data on SD Card!");
	      				m_fwSensorRecord = null;
	      				return;
	      			}
     			}
     			     			
     			//Disable setting for sensor when recording        			
     			enableSensorSetting(false);

     	        m_btnRecord.setText(getString(R.string.btn_stop));
     	        if (blnSensorSelected) {
     	        	sShowInfo = "Recording:" + m_sFullPathFile;
     	        }
     	             	        
     			m_tvShowInfo.setText(sShowInfo);
     			
     	        //Save to file once an interval
     			m_blnRecordStatus = true;
     			
     			if ((m_blnRecordCont == false) && (m_lSensorDataFileInterval != 0)) {
     				//Need to stop after an interval
     				m_receiverAlarm = new AlarmReceiver();
     				registerReceiver(m_receiverAlarm, new IntentFilter(getString(R.string.myalarm)));
     				startTimer();
     			}
     			     			
     		} else {
     			
     			//Stop record. Close Sensor Record File
     			if (m_fwSensorRecord != null) {
     				try {
     					m_fwSensorRecord.close();
         				m_fwSensorRecord = null;
     				} catch (IOException e) {
     					//
     				}
     			}
     			     			
     			// Enable sensor setting when recording stops
     			enableSensorSetting(true);
     	        m_tvShowInfo.setText(getString(R.string.defaultinfo));        			
     	        m_btnRecord.setText(getString(R.string.btn_start));
     	        m_tvShowRecord.setText("");
     	        resetValues();
     			m_blnRecordStatus = false;

     			if ((m_blnRecordCont == false) && (m_lSensorDataFileInterval != 0)) {
     				//Need to stop after an interval
     				stopTimer();
     				unregisterReceiver(m_receiverAlarm);
     			}     			
     		}
     	}
     };

     /* Check the availability of sensors, disable relative widgets */
     private void checkSensorAvailability() {
    	 List<Sensor> lstSensor = m_smPCO.getSensorList(Sensor.TYPE_ORIENTATION);
    	 if (lstSensor.size() > 0) {
    		 m_blnOrientPresent = true;
    	 } else {
    		 m_blnOrientPresent = false;
    		 m_blnOrientEnabled = false;
    		 m_chkOrient.setEnabled(false);
    		 m_rdSensorOrient.setEnabled(false);
    	 }
    	
    	 lstSensor = m_smPCO.getSensorList(Sensor.TYPE_GYROSCOPE);
    	 if (lstSensor.size() > 0) {
    		 m_blnGyroPresent = true;
    	 } else {
    		 m_blnGyroPresent = false;
    		 m_blnGyroEnabled = false;
    		 m_chkGyro.setEnabled(false);
    		 m_rdSensorGyro.setEnabled(false);
    	 }

    	 lstSensor = m_smPCO.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION);
    	 if (lstSensor.size() > 0) {
    		 m_blnAcclPresent = true;
    	 } else {
    		 m_blnAcclPresent = false;
    		 m_blnAcclEnabled = false;
    		 m_chkAccl.setEnabled(false);
    		 m_rdSensorAccl.setEnabled(false);
    	 }

    	 lstSensor = m_smPCO.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
    	 if (lstSensor.size() > 0) {
    		 m_blnMagnetPresent = true;
    	 } else {
    		 m_blnMagnetPresent = false;
    		 m_blnMagnetEnabled = false;
    		 m_chkMagnet.setEnabled(false);
    		 m_rdSensorMagnet.setEnabled(false);
    	 }

    	 lstSensor = m_smPCO.getSensorList(Sensor.TYPE_PROXIMITY);
    	 if (lstSensor.size() > 0) {
    		 m_blnProximityPresent = true;
    	 } else {
    		 m_blnProximityPresent = false;
    		 m_blnProximityEnabled = false;
    		 m_chkProximity.setEnabled(false);
    		 m_rdSensorProximity.setEnabled(false);
    	 }

    	 lstSensor = m_smPCO.getSensorList(Sensor.TYPE_LIGHT);
    	 if (lstSensor.size() > 0) {
    		 m_blnLightPresent = true;
    	 } else {
    		 m_blnLightPresent = false;
    		 m_blnLightEnabled = false;
    		 m_chkLight.setEnabled(false);
    		 m_rdSensorLight.setEnabled(false);
    	 }
    }
    
     /* Calculate orientation from accelerometer and magnetic field value */
    private boolean calculateOrientation() {
    	float[] arrfValues = new float[3];
    	float[] arrfR = new float[9];
    	float[] arrfI = new float[9];
    	
//    	if ((m_arrfAcclValues == null) || (m_arrfMagnetValues == null) || (m_blnOrientValid == false)) {
        if ((m_arrfAcclValues == null) || (m_arrfMagnetValues == null)) {

    		Log.i(TAG, "m_arrfAcclValues or m_arrfMagnetValues is NULL");
    		return false;
    	}
    	
    	//m_blnOrientValid = false;
    	
    	if (SensorManager.getRotationMatrix(arrfR, arrfI, m_arrfAcclValues, m_arrfMagnetValues)) {
    		SensorManager.getOrientation(arrfR, arrfValues);
    		m_arrfOrientValues[0] = (float)Math.toDegrees(arrfValues[0]);
    		m_arrfOrientValues[1] = (float)Math.toDegrees(arrfValues[1]);
    		m_arrfOrientValues[2] = (float)Math.toDegrees(arrfValues[2]);
//    		m_arrfOrientValues[0] = values[0];
//    		m_arrfOrientValues[1] = values[1];
//    		m_arrfOrientValues[2] = values[2];
    		return true;
    	} else {
    		return false;
    	}
    	
    }
     
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	int i;
     	
    	super.onCreate(savedInstanceState);

        m_smPCO = (SensorManager) getSystemService(SENSOR_SERVICE);

        setContentView(R.layout.main);
        
        m_chkOrient = (CheckBox)findViewById(R.id.chkOrient);
        m_chkGyro = (CheckBox)findViewById(R.id.chkGyro);
        m_chkAccl = (CheckBox)findViewById(R.id.chkAccl);
        m_chkMagnet = (CheckBox)findViewById(R.id.chkMagnet);
        m_chkProximity = (CheckBox)findViewById(R.id.chkProximity);
        m_chkLight = (CheckBox)findViewById(R.id.chkLight);
        
        
        m_chkOrient.setOnCheckedChangeListener(m_chkSensorEnableListener);
        m_chkGyro.setOnCheckedChangeListener(m_chkSensorEnableListener);
        m_chkAccl.setOnCheckedChangeListener(m_chkSensorEnableListener);
        m_chkMagnet.setOnCheckedChangeListener(m_chkSensorEnableListener);
        m_chkProximity.setOnCheckedChangeListener(m_chkSensorEnableListener);
        m_chkLight.setOnCheckedChangeListener(m_chkSensorEnableListener);
                
        m_rdgpSensor = (RadioGroup)findViewById(R.id.RdGpSensor);
    	m_rdSensorOrient = (RadioButton)findViewById(R.id.RdSensor_Orient);
        m_rdSensorGyro = (RadioButton)findViewById(R.id.RdSensor_Gyro);
    	m_rdSensorAccl = (RadioButton)findViewById(R.id.RdSensor_Accl);
    	m_rdSensorMagnet = (RadioButton)findViewById(R.id.RdSensor_Magnet);
    	m_rdSensorProximity = (RadioButton)findViewById(R.id.RdSensor_Proximity);
    	m_rdSensorLight = (RadioButton)findViewById(R.id.RdSensor_Light);
       	
        m_rdgpSensorMode = (RadioGroup)findViewById(R.id.RdGpSensorMode);
        m_rdSensorModeFastest = (RadioButton)findViewById(R.id.RdSensorMode_Fastest);
        m_rdSensorModeGame = (RadioButton)findViewById(R.id.RdSensorMode_Game);
        m_rdSensorModeNormal = (RadioButton)findViewById(R.id.RdSensorMode_Normal);
        m_rdSensorModeUI = (RadioButton)findViewById(R.id.RdSensorMode_UI);
        
        m_spnSelectFileInterval = (Spinner)findViewById(R.id.spnSelectFileInterval);
        
        m_chkRecCont = (CheckBox)findViewById(R.id.chkRecCont);
        m_chkRecCont.setOnCheckedChangeListener(m_chkRecContEnableListener);
        
        m_tvShowInfo = (TextView)findViewById(R.id.ShowInfo);  //Show Message to user
        m_tvShowInfo.setText(getString(R.string.defaultinfo));
        m_btnRecord = (Button)findViewById(R.id.Record);
        m_btnRecord.setText(getString(R.string.btn_start));
        m_tvShowRecord = (TextView)findViewById(R.id.ShowRecord); //Show sensor record
        
        m_btnConnect = (Button)findViewById(R.id.Connect);
        m_btnConnect.setText(getString(R.string.btn_connect));
        m_edtServerIP = (EditText)findViewById(R.id.ServerAddress);
        m_edtServerPort = (EditText)findViewById(R.id.ServerPort);
        
        for (i = 0; i < m_arrsFileIntervalOptions.length; i++) {
        	m_lstInterval.add(m_arrsFileIntervalOptions[i]);
        }
                
        m_adpInterval = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,m_lstInterval);
        m_adpInterval.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_spnSelectFileInterval.setAdapter(m_adpInterval);
        
        //setDefaultStatus();
      
        PackageManager pm = getPackageManager();
        m_riHome = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);

        m_rdgpSensor.setOnCheckedChangeListener(m_rdgpSensorListener);
        m_rdgpSensorMode.setOnCheckedChangeListener(m_rdgpSensorModeListener);
        m_spnSelectFileInterval.setOnItemSelectedListener(m_spnListener);
        m_btnRecord.setOnClickListener(m_btnRecordListener);
        m_btnConnect.setOnClickListener(m_btnConnectListener);
        
        checkSensorAvailability();
        				
        /* No sensor is installed, disable other widgets and show information to user */
		if ((m_blnOrientPresent == false) && 
			(m_blnGyroPresent == false) &&
			(m_blnAcclPresent == false) &&
			(m_blnMagnetPresent == false) &&
			(m_blnProximityPresent == false) &&
			(m_blnLightPresent == false)) {
			m_rdSensorModeFastest.setEnabled(false);
			m_rdSensorModeGame.setEnabled(false);
			m_rdSensorModeNormal.setEnabled(false);
			m_rdSensorModeUI.setEnabled(false);
			
		}
		
		m_edtServerIP.setText("");
		m_edtServerPort.setText("8888");
				
    }
    
    public void startActivitySafely(Intent intent) {
    	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	try {
    		startActivity(intent);
    	} catch (ActivityNotFoundException e) {
    		Toast.makeText(this, "unable to open", Toast.LENGTH_SHORT).show();
    	} catch (SecurityException e) {
    		Toast.makeText(this, "unable to open", Toast.LENGTH_SHORT).show();
    	}
    	
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_BACK) {
    		//Show MAIN app without finishing current activity
    		ActivityInfo ai = m_riHome.activityInfo;
    		Intent startIntent = new Intent(Intent.ACTION_MAIN);
    		startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    		startIntent.setComponent(new ComponentName(ai.packageName,ai.name));
    		startActivitySafely(startIntent);
    		return true;
    	} else {
    		return super.onKeyDown(keyCode, event);
    	}
    }
    
    protected void onResume() {
//    	int nModeType;
    	super.onResume();
    }
        
    protected void onStop() {
    	super.onStop();    	
    }
    
    
    public void onSensorChanged(SensorEvent event) {
    	
    	recordSensorGPS(DATA_TYPE_SENSOR, event, null);
    }
    
    public void recordSensorGPS(int iType, SensorEvent event,Location location) {
    	String sRecordLine;
    	String sTimeField;
    	Date dtCurDate;
    	long lStartTime = 0;
    	long lCurrentTime = 0;
    	long lElapsedTime = 0;
		SimpleDateFormat spdRecordTime,spdCurDateTime;
        final String DATE_FORMAT = "yyyyMMddHHmmss";
		final String DATE_FORMAT_S = "yyMMddHHmmssSSS"; //"yyyyMMddHHmmssSSS"
		String sSendMsg = "";
		String sSendMsgHead = "";
		String sSendMsgBody = "";
        
        if (m_blnRecordStatus == false) { //Stopped
        	return;
        }

        dtCurDate = new Date();
		
        if (m_lSensorDataFileInterval != 0) {
        	// Need to check whether to split file
        	lCurrentTime = dtCurDate.getTime();
			lStartTime = m_dtFileStart.getTime();
			
			//Check whether to record sensor data to a new file
			if (lCurrentTime - lStartTime >= m_lSensorDataFileInterval) {
				if (m_fwSensorRecord != null) {
					//Close current file
					try {
						m_fwSensorRecord.close();
						m_fwSensorRecord = null;
					} catch (IOException e) {
						
					}
				}
				
				if (m_blnRecordCont == false) {
					//Stop recording
					
				}
				
				if (m_fwSensorRecord == null) {
					//Create a new file to record sensor data
					spdCurDateTime = new SimpleDateFormat(DATE_FORMAT);
	//				m_sRecordFile = curDateTime.format(curDate) + ".csv";
					m_sRecordFile = spdCurDateTime.format(dtCurDate);
					m_sFullPathFile = Environment.getExternalStorageDirectory().getAbsolutePath() + 
																				File.separator + 
																				getString(R.string.sensordata_folder) + 
																				File.separator + m_sRecordFile;
					// Append file type information to the file name
					m_sFullPathFile = m_sFullPathFile + getFileType();
					
	    			try {
	    				m_fwSensorRecord = new FileWriter(m_sFullPathFile);
	     			} catch (IOException e) {
	     				m_tvShowInfo.setText("Failed to record sensor data on SD Card!");
	     				m_fwSensorRecord = null;
	     				return;
	     			} 
	    			
	    			m_dtFileStart = dtCurDate;
	    			
	    			m_tvShowInfo.setText("Recording:" + m_sFullPathFile);
				}	
			}
        }
        
		// Timestamp for the record
        spdRecordTime = new SimpleDateFormat(DATE_FORMAT_S);
		sTimeField = spdRecordTime.format(dtCurDate);
		
		lElapsedTime = System.nanoTime(); //New Added
		
		if (iType == DATA_TYPE_SENSOR) {
			synchronized(this) {
	    		switch (event.sensor.getType()){
	//	    		case Sensor.TYPE_ORIENTATION:	    			
		    			/* Azimuth(between Y-axis and magnetic north,round Z), 
		    			 * Pitch(-180~180, around x-axis, positive: z-axis moves toward y-axis), 
		    			 * Roll(-90~90,around y-axis, positive: x-axis moves toward z-axis),
		    			 * All angles in degrees
		    			 */
	//	    			strOrient = Float.toString(event.values[0]) + "," + 
	//	    						Float.toString(event.values[1]) + "," + 
	//	    						Float.toString(event.values[2]) + ",";
		
	//	    			break;
		
		    		case Sensor.TYPE_GYROSCOPE:
		    			//X,Y,Z
		    			m_sGyro = Float.toString(event.values[0]) + "," + 
		    					  Float.toString(event.values[1]) + "," + 
		    					  Float.toString(event.values[2]) + ",";
		
		    			break;
		    			
		    		case Sensor.TYPE_ACCELEROMETER:
		    			if (m_blnOrientEnabled) {
			    			m_arrfAcclValues = event.values.clone();
		    				
			    			if (calculateOrientation()) {
		    					m_sOrient = Float.toString(m_arrfOrientValues[0]) + "," + 
		    								Float.toString(m_arrfOrientValues[1]) + "," + 
		    								Float.toString(m_arrfOrientValues[2]) + ",";
		    				}
		    			}
		    			break;
		    			
		    		case Sensor.TYPE_LINEAR_ACCELERATION:
		    			//X,Y,Z
		    			m_sAccl = Float.toString(event.values[0]) + "," + 
		    					  Float.toString(event.values[1]) + "," + 
		    					  Float.toString(event.values[2]) + ",";
		    			
		    			break;
		    			
		    		case Sensor.TYPE_MAGNETIC_FIELD:
		    			// Values are in micro-Tesla (uT) and measure the ambient magnetic field 
		    			if (m_blnMagnetEnabled) {
		    				m_sMagnet = Float.toString(event.values[0]) + "," + 
		    							Float.toString(event.values[1]) + "," + 
		    							Float.toString(event.values[2]) + ",";
		    			}
	
		    			if (m_blnOrientEnabled) {
		    				m_arrfMagnetValues = event.values.clone();
		    				//m_blnOrientValid = true;
		    				
		    				if (calculateOrientation()) {
		    					m_sOrient = Float.toString(m_arrfOrientValues[0]) + "," + 
	    									Float.toString(m_arrfOrientValues[1]) + "," + 
	    									Float.toString(m_arrfOrientValues[2]) + ",";
		    				}
		    			}
		    			
		    			break;
		    		
		    		case Sensor.TYPE_PROXIMITY:
		    			// Proximity sensor distance measured in centimeters 
		    			m_sProximity = Float.toString(event.values[0]) + ",";
		    			
		    			break;
		    		
		    		case Sensor.TYPE_LIGHT:
		    			// Ambient light level in SI lux units 
		    			m_sLight = Float.toString(event.values[0]) + ",";
		    			
		    			break;
		    		
		    		case Sensor.TYPE_GRAVITY:
	    				m_sGravity = Float.toString(event.values[0]) + "," + 
    								 Float.toString(event.values[1]) + "," + 
    								 Float.toString(event.values[2]) + ",";
	    				break;
	    		}
	    	}
		} 
		
		sRecordLine = sTimeField + ",";
		sRecordLine = sRecordLine + Long.valueOf(lElapsedTime).toString() + ",";   //New Added
		
		sSendMsgHead = sTimeField + ",";
		
		
		//Add the timestamp from 1970.1.1
		if (iType == DATA_TYPE_SENSOR) {
			//Sensor Data, Timestamp in nanosecond
			sRecordLine = sRecordLine + Long.valueOf(event.timestamp).toString() + ",";
		} 

		if (m_blnOrientEnabled) {
			sRecordLine = sRecordLine + m_sOrient;
			sSendMsgHead = sSendMsgHead + "O";
			sSendMsgBody = sSendMsgBody + m_sOrient;
		}
		
		if (m_blnGravityEnabled) {
			sRecordLine = sRecordLine + m_sGravity;
		}
		
		if (m_blnGyroEnabled) {
			sRecordLine = sRecordLine + m_sGyro;
			sSendMsgHead = sSendMsgHead + "G";
			sSendMsgBody = sSendMsgBody + m_sGyro;
		}
		
		if (m_blnAcclEnabled) {
			sRecordLine = sRecordLine + m_sAccl;
			sSendMsgHead = sSendMsgHead + "A";
			sSendMsgBody = sSendMsgBody + m_sAccl;
		}
		
		if (m_blnMagnetEnabled) {
			sRecordLine = sRecordLine + m_sMagnet;			
		}

		if (m_blnProximityEnabled) {
			sRecordLine = sRecordLine + m_sProximity;			
		}

		if (m_blnLightEnabled) {
			sRecordLine = sRecordLine + m_sLight;			
		}
		
		
		if (m_blnConnected == true) {
			
			sSendMsg = sSendMsgHead + "," + sSendMsgBody;
			
			//Send sensor information to PC side
			if (sSendMsg.length() > 0) {
				byte sndData[] = sSendMsg.getBytes();
				try {
					DatagramPacket packet = new DatagramPacket(sndData, sndData.length, m_serverAddress, m_iServerPort);
					//Send sensor reading to server
					m_cltSocket.send(packet);
					//m_tvShowRecord.setText("Send:" + sSendMsg);
				} catch (Exception e) {
					m_tvShowRecord.setText("Sending Failed!");
					//
				}
			}
			
		}
		
		sRecordLine = sRecordLine + m_sSensorAccuracy;
		
    	sRecordLine = sRecordLine + System.getProperty("line.separator");

    	if (m_blnGyroEnabled == false &&  m_blnAcclEnabled == false) {
    		//To avoid frequently update GUI, only when low-frequent data is presented, show on GUI
    		//Gyro and Accl are too frequent
    		m_tvShowRecord.setText(sRecordLine);
    	}
    	
    	if (m_fwSensorRecord != null) {
			//Write information into file
			//Compose information into recordLine
    		try {
    			m_fwSensorRecord.write(sRecordLine);
    		} catch (IOException e) {
    			
    		}
    	}

    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    	switch(sensor.getType()) {
    		case Sensor.TYPE_GYROSCOPE:
    			m_sSensorAccuracy = "1,"; //Gyro
    			break;

    		case Sensor.TYPE_ACCELEROMETER:
    			m_sSensorAccuracy = "2,"; //Accl
    			break;

    		case Sensor.TYPE_LINEAR_ACCELERATION:
    			m_sSensorAccuracy = "3,"; //LinearAccl
    			break;

    		case Sensor.TYPE_MAGNETIC_FIELD:
    			m_sSensorAccuracy = "4,"; //Magnet
    			break;
    			
    		case Sensor.TYPE_PROXIMITY:
    			m_sSensorAccuracy = "5,"; //Proxi
    			break;

    		case Sensor.TYPE_LIGHT:
    			m_sSensorAccuracy = "6,"; //Light
    			break;
    		
    		case Sensor.TYPE_GRAVITY:
    			m_sSensorAccuracy = "7,"; //Gravity		
    			break;
    			
    		default:
    			m_sSensorAccuracy = "8,"; //Other
    	}
    	
    	switch (accuracy) {
    		case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
    			m_sSensorAccuracy = m_sSensorAccuracy + "1,"; //H
    			break;

    		case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
    			m_sSensorAccuracy = m_sSensorAccuracy + "2,"; //M
    			break;
    			
    		case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
    			m_sSensorAccuracy = m_sSensorAccuracy + "3,"; //L
    			break;
    			
    		case SensorManager.SENSOR_STATUS_UNRELIABLE:
    			m_sSensorAccuracy = m_sSensorAccuracy + "4,"; //U
    			break;
    	}
    }
}
