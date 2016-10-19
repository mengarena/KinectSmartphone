//------------------------------------------------------------------------------
// <copyright file="SkeletalViewer.cpp" company="Microsoft">
//     Copyright (c) Microsoft Corporation.  All rights reserved.
// </copyright>
//------------------------------------------------------------------------------

// This module provides sample code used to demonstrate Kinect NUI processing

// Note: 
//     Platform SDK lib path should be added before the VC lib
//     path, because uuid.lib in VC lib path may be older

#include "stdafx.h"
#include <strsafe.h>
#include "SkeletalViewer.h"
#include "resource.h"
#include <string>
#include <fstream>
#include <sstream>
#include <iomanip>


using namespace std;

// Global Variables:
CSkeletalViewerApp  g_skeletalViewerApp;  // Application class

#define INSTANCE_MUTEX_NAME L"SkeletalViewerInstanceCheck"
string DATA_FOLDER = "E:\\KinectData\\";
string DEPTH_TIMESTAMP_FILE = "depth_timestamp.txt";

//-------------------------------------------------------------------
// _tWinMain
//
// Entry point for the application
//-------------------------------------------------------------------
int APIENTRY _tWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPTSTR lpCmdLine, int nCmdShow)
{
    MSG       msg;
    WNDCLASS  wc;
        
    // unique mutex, if it already exists there is already an instance of this app running
    // in that case we want to show the user an error dialog
    HANDLE hMutex = CreateMutex( NULL, FALSE, INSTANCE_MUTEX_NAME );
    if ( (hMutex != NULL) && (GetLastError() == ERROR_ALREADY_EXISTS) ) 
    {
        TCHAR szAppTitle[256] = { 0 };
        TCHAR szRes[512] = { 0 };

        //load the app title
        LoadString( hInstance, IDS_APPTITLE, szAppTitle, _countof(szAppTitle) );

        //load the error string
        LoadString( hInstance, IDS_ERROR_APP_INSTANCE, szRes, _countof(szRes) );

        MessageBox( NULL, szRes, szAppTitle, MB_OK | MB_ICONHAND );

        CloseHandle(hMutex);
        return -1;
    }

    // Store the instance handle
    g_skeletalViewerApp.m_hInstance = hInstance;

    // Dialog custom window class
    ZeroMemory( &wc,sizeof(wc) );
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.cbWndExtra = DLGWINDOWEXTRA;
    wc.hInstance = hInstance;
    wc.hCursor = LoadCursor(NULL,IDC_ARROW);
    wc.hIcon = LoadIcon(hInstance,MAKEINTRESOURCE(IDI_SKELETALVIEWER));
    wc.lpfnWndProc = DefDlgProc;
    wc.lpszClassName = SZ_APPDLG_WINDOW_CLASS;
    if( !RegisterClass(&wc) )
    {
        return 0;
    }

    // Create main application window
    HWND hWndApp = CreateDialogParam(
        hInstance,
        MAKEINTRESOURCE(IDD_APP),
        NULL,
        (DLGPROC) CSkeletalViewerApp::MessageRouter, 
        reinterpret_cast<LPARAM>(&g_skeletalViewerApp));

    // Show window
    ShowWindow(hWndApp,nCmdShow); 

    // Main message loop:
    while( GetMessage( &msg, NULL, 0, 0 ) ) 
    {
        // If a dialog message will be taken care of by the dialog proc
        if ( (hWndApp != NULL) && IsDialogMessage(hWndApp, &msg) )
        {
            continue;
        }

        // otherwise do our window processing
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
	

    CloseHandle( hMutex );

    return static_cast<int>(msg.wParam);
}


/////////////////////////////////////////////////////////////////////
//Add by Meng
//Convert string to WCHAR
WCHAR* ConvertByteToWideChar(string strOrg)
{
	int size = strOrg.length();
	WCHAR *buffer = new WCHAR[size+1];

	MultiByteToWideChar(CP_ACP, 0, strOrg.c_str(), size, buffer, size*sizeof(WCHAR));
	buffer[size] = 0;

	return buffer;
}


//Check whether the folder exists
BOOL FolderExists(string s)
{
	DWORD attr;
	attr = GetFileAttributes(ConvertByteToWideChar(s));
	return (attr != (DWORD)(-1)) && ( attr & FILE_ATTRIBUTE_DIRECTORY);
}


//Create folder recursively
BOOL CreateDir(string P)
{
	int len = P.length();

	if (len < 2)
	{
		return FALSE;
	}

	if ('\\' == P[len - 1])
	{
		P = P.substr(0, len - 1);
		len = P.length();
	}

	if (len <= 0)
	{
		return FALSE;
	}

	if (len <= 3)
	{
		if (FolderExists(P))
		{
			return TRUE;
		}
		else
		{
			return FALSE;
		}
	}

	if (FolderExists(P))
	{
		return TRUE;
	}

	string Parent;

	Parent = P.substr(0, P.rfind('\\'));

	if (Parent.length() <= 0)
	{
		return FALSE;
	}

	BOOL ret = CreateDir(Parent);
	if (ret)
	{
		SECURITY_ATTRIBUTES sa;
		sa.nLength = sizeof(SECURITY_ATTRIBUTES);
		sa.lpSecurityDescriptor = NULL;
		sa.bInheritHandle = 0;
		ret = ::CreateDirectoryA(P.c_str(), &sa); 
		return ret;
	}
	else
	{
		return FALSE;
	}

}


/////////////////////////////////////////////////////////////////////

//-------------------------------------------------------------------
// Constructor
//-------------------------------------------------------------------
CSkeletalViewerApp::CSkeletalViewerApp() : m_hInstance(NULL)
{
    ZeroMemory(m_szAppTitle, sizeof(m_szAppTitle));
    LoadString(m_hInstance, IDS_APPTITLE, m_szAppTitle, _countof(m_szAppTitle));

    m_fUpdatingUi = false;
    Nui_Zero();

    // Init Direct2D
    D2D1CreateFactory( D2D1_FACTORY_TYPE_SINGLE_THREADED, &m_pD2DFactory );
}

//-------------------------------------------------------------------
// Destructor
//-------------------------------------------------------------------
CSkeletalViewerApp::~CSkeletalViewerApp()
{
    // Clean up Direct2D
    SafeRelease( m_pD2DFactory );

    Nui_Zero();
    SysFreeString(m_instanceId);
}

void CSkeletalViewerApp::ClearComboBox()
{
    for ( long i = 0; i < SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_GETCOUNT, 0, 0); i++ )
    {
        SysFreeString( reinterpret_cast<BSTR>( SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_GETITEMDATA, i, 0) ) );
    }
    SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_RESETCONTENT, 0, 0);
}

void CSkeletalViewerApp::UpdateComboBox()
{
    m_fUpdatingUi = true;
    ClearComboBox();

    int numDevices = 0;
    HRESULT hr = NuiGetSensorCount(&numDevices);

    if ( FAILED(hr) )
    {
        return;
    }

    EnableWindow(GetDlgItem(m_hWnd, IDC_APPTRACKING), numDevices > 0);

    long selectedIndex = 0;
    for ( int i = 0; i < numDevices; i++ )
    {
        INuiSensor *pNui = NULL;
        HRESULT hr = NuiCreateSensorByIndex(i,  &pNui);
        if (SUCCEEDED(hr))
        {
            HRESULT status = pNui ? pNui->NuiStatus() : E_NUI_NOTCONNECTED;
            if (status == E_NUI_NOTCONNECTED)
            {
                pNui->Release();
                continue;
            }
            
            WCHAR kinectName[MAX_PATH];
            StringCchPrintfW( kinectName, _countof(kinectName), L"Kinect %d", i);
            long index = static_cast<long>( SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(kinectName)) );
            SendDlgItemMessage( m_hWnd, IDC_CAMERAS, CB_SETITEMDATA, index, reinterpret_cast<LPARAM>(pNui->NuiUniqueId()) );
            if (m_pNuiSensor && pNui == m_pNuiSensor)
            {
                selectedIndex = index;
            }
            pNui->Release();
        }
    }

    SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_SETCURSEL, selectedIndex, 0);
    m_fUpdatingUi = false;
}

void CSkeletalViewerApp::UpdateTrackingComboBoxes()
{
    SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_RESETCONTENT, 0, 0);
    SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_RESETCONTENT, 0, 0);

    SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"0"));
    SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"0"));
    
    SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_SETITEMDATA, 0, 0);
    SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_SETITEMDATA, 0, 0);

    bool setCombo0 = false;
    bool setCombo1 = false;
    
    for ( int i = 0 ; i < NUI_SKELETON_COUNT ; i++ )
    {
        if ( m_SkeletonIds[i] != 0 )
        {
            WCHAR trackingId[MAX_PATH];
            StringCchPrintfW(trackingId, _countof(trackingId), L"%d", m_SkeletonIds[i]);
            LRESULT pos0 = SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(trackingId));
            LRESULT pos1 = SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(trackingId));

            SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_SETITEMDATA, pos0, m_SkeletonIds[i]);
            SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_SETITEMDATA, pos1, m_SkeletonIds[i]);

            if ( m_TrackedSkeletonIds[0] == m_SkeletonIds[i] )
            {
                SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_SETCURSEL, pos0, 0);
                setCombo0 = true;
            }

            if ( m_TrackedSkeletonIds[1] == m_SkeletonIds[i] )
            {
                SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_SETCURSEL, pos1, 0);
                setCombo1 = true;
            }
        }
    }
    
    if ( !setCombo0 )
    {
        SendDlgItemMessage( m_hWnd, IDC_TRACK0, CB_SETCURSEL, 0, 0 );
    }

    if ( !setCombo1 )
    {
        SendDlgItemMessage( m_hWnd, IDC_TRACK1, CB_SETCURSEL, 0, 0 );
    }
}

void CSkeletalViewerApp::UpdateTrackingFromComboBoxes()
{
    LRESULT trackingIndex0 = SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_GETCURSEL, 0, 0);
    LRESULT trackingIndex1 = SendDlgItemMessage(m_hWnd, IDC_TRACK1, CB_GETCURSEL, 0, 0);

    LRESULT trackingId0 = SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_GETITEMDATA, trackingIndex0, 0);
    LRESULT trackingId1 = SendDlgItemMessage(m_hWnd, IDC_TRACK0, CB_GETITEMDATA, trackingIndex1, 0);

    Nui_SetTrackedSkeletons(static_cast<int>(trackingId0), static_cast<int>(trackingId1));
}

LRESULT CALLBACK CSkeletalViewerApp::MessageRouter( HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam )
{
    CSkeletalViewerApp *pThis = NULL;
    
    if ( WM_INITDIALOG == uMsg )
    {
        pThis = reinterpret_cast<CSkeletalViewerApp*>(lParam);
        SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(pThis));
        NuiSetDeviceStatusCallback( &CSkeletalViewerApp::Nui_StatusProcThunk, pThis );
    }
    else
    {
        pThis = reinterpret_cast<CSkeletalViewerApp*>(::GetWindowLongPtr(hwnd, GWLP_USERDATA));
    }

    if ( NULL != pThis )
    {
        return pThis->WndProc( hwnd, uMsg, wParam, lParam );
    }

    return 0;
}

//-------------------------------------------------------------------
// WndProc
//
// Handle windows messages
//-------------------------------------------------------------------
LRESULT CALLBACK CSkeletalViewerApp::WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    switch(message)
    {
        case WM_INITDIALOG:
        {
            LOGFONT lf;

            // Clean state the class
            Nui_Zero();

            // Bind application window handle
            m_hWnd = hWnd;

            // Set the font for Frames Per Second display
            GetObject( (HFONT) GetStockObject(DEFAULT_GUI_FONT), sizeof(lf), &lf );
            lf.lfHeight *= 4;
            m_hFontFPS = CreateFontIndirect(&lf);
            SendDlgItemMessage( hWnd, IDC_FPS, WM_SETFONT, (WPARAM) m_hFontFPS, 0 );

            UpdateComboBox();
            SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_SETCURSEL, 0, 0);
			
			//Add by Meng
			EnableWindow( GetDlgItem(m_hWnd, IDC_BTN_CTRL), false );

            // Initialize and start NUI processing
            Nui_Init();

			//Add by Meng
			// Get and Show local IP
			string strIP = getLocalIP();
			if (strIP.length() > 0) 
			{
				SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_IP), ConvertByteToWideChar(strIP));
			}

			// Start a thread to process socket communication with smartphone
			m_hThSocketProcess = CreateThread(NULL, 0, Socket_ProcessThread, this, 0, NULL);


        }
        break;

        case WM_USER_UPDATE_FPS:
        {
            ::SetDlgItemInt( m_hWnd, static_cast<int>(wParam), static_cast<int>(lParam), FALSE );
        }
        break;

        case WM_USER_UPDATE_COMBO:
        {
            UpdateComboBox();
        }
        break;

        case WM_COMMAND:
        {
            if( HIWORD( wParam ) == CBN_SELCHANGE )
            {
                switch (LOWORD(wParam))
                {
                    case IDC_CAMERAS:
                    {
                        LRESULT index = ::SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_GETCURSEL, 0, 0);

                        // Don't reconnect as a result of updating the combo box
                        if ( !m_fUpdatingUi )
                        {
                            Nui_UnInit();
                            Nui_Zero();
                            Nui_Init(reinterpret_cast<BSTR>(::SendDlgItemMessage(m_hWnd, IDC_CAMERAS, CB_GETITEMDATA, index, 0)));
                        }
                    }
                    break;

                    case IDC_TRACK0:
                    case IDC_TRACK1:
                    {
                        UpdateTrackingFromComboBoxes();
                    }
                    break;
                }
            }
            else if ( HIWORD( wParam ) == BN_CLICKED )
            {
                switch (LOWORD(wParam))
                {
                    case IDC_APPTRACKING:
                    {
                        bool checked = IsDlgButtonChecked(m_hWnd, IDC_APPTRACKING) == BST_CHECKED;
                        m_bAppTracking = checked;

                        EnableWindow( GetDlgItem(m_hWnd, IDC_TRACK0), checked );
                        EnableWindow( GetDlgItem(m_hWnd, IDC_TRACK1), checked );

                        if ( checked )
                        {
                            UpdateTrackingComboBoxes();
                        }

                        Nui_SetApplicationTracking(checked);

                        if ( checked )
                        {
                            UpdateTrackingFromComboBoxes();
                        }
                    }
                    break;

					//Add by Meng
					case IDC_CHK_DEPTH:
					{
						bool checked = IsDlgButtonChecked(m_hWnd, IDC_CHK_DEPTH) == BST_CHECKED;
						m_bRecordDepth = checked;
						UpdateControlButton();
					}
					break;

					case IDC_CHK_SKELETON:
					{
						bool checked = IsDlgButtonChecked(m_hWnd, IDC_CHK_SKELETON) == BST_CHECKED;
						m_bRecordSkeleton = checked;
						UpdateControlButton();
					}
					break;

					case IDC_CHK_COLOR:
					{
						bool checked = IsDlgButtonChecked(m_hWnd, IDC_CHK_COLOR) == BST_CHECKED;
						m_bRecordColor = checked;
						UpdateControlButton();
					}
					break;

					case IDC_CHK_SENSOR:
					{
						bool checked = IsDlgButtonChecked(m_hWnd, IDC_CHK_SENSOR) == BST_CHECKED;
						m_bRecordSensor = checked;
						UpdateControlButton();
					}
					break;

					case IDC_BTN_CTRL:
					{
						ControlKinectRecord();
					}
					break;
                }
            }
        }
        break;

        // If the titlebar X is clicked destroy app
        case WM_CLOSE:
            DestroyWindow(hWnd);
            break;

        case WM_DESTROY:
            // Uninitialize NUI
            Nui_UnInit();

            // Other cleanup
            ClearComboBox();
            DeleteObject(m_hFontFPS);

            // Quit the main message pump
            PostQuitMessage(0);
            break;
    }

    return FALSE;
}

//-------------------------------------------------------------------
// MessageBoxResource
//
// Display a MessageBox with a string table table loaded string
//-------------------------------------------------------------------
int CSkeletalViewerApp::MessageBoxResource( UINT nID, UINT nType )
{
    static TCHAR szRes[512];

    LoadString( m_hInstance, nID, szRes, _countof(szRes) );
    return MessageBox( m_hWnd, szRes, m_szAppTitle, nType );
}

//-------------------------------------------------------------------
// Add by Meng
//
// Update control button
//-------------------------------------------------------------------
void CSkeletalViewerApp::UpdateControlButton()
{
	bool bEnable;

	bEnable = m_bRecordDepth || m_bRecordSkeleton || m_bRecordColor || m_bRecordSensor;
	EnableWindow( GetDlgItem(m_hWnd, IDC_BTN_CTRL), bEnable );
	
	if (bEnable == false) 
	{
		SendDlgItemMessage( m_hWnd, IDC_BTN_CTRL, WM_SETTEXT, 0,  (LPARAM)(TEXT("Start")) );
		m_bStarted = false;
	}
}


// Control the recording of Kinect Data
void CSkeletalViewerApp::ControlKinectRecord()
{
	if (m_bStarted == false) 
	{
		//User pressed the "Start" button
		//Start recording Kinect Data
		//Create the folder to save the Kinect Data in this run
		SYSTEMTIME st;
		BOOL bRet;
		ostringstream tmpDataFolder;
									
		::GetLocalTime(&st);

		tmpDataFolder<<DATA_FOLDER<<::setw(4)<<::setfill('0')<<st.wYear<<::setw(2)<<::setfill('0')<<st.wMonth<<::setw(2)<<::setfill('0')<<st.wDay<<::setw(2)<<::setfill('0')<<st.wHour<<::setw(2)<<::setfill('0')<<st.wMinute<<::setw(2)<<::setfill('0')<<st.wSecond;
		m_strDataFolder = tmpDataFolder.str();

		if (FolderExists(m_strDataFolder) == FALSE)
		{
			bRet = CreateDir(m_strDataFolder);
			if (bRet == FALSE) 
			{
				::MessageBoxA(NULL,"Failed to create folder to store Kinect data!", "Create Folder", MB_OK | MB_ICONHAND);
				return;
			}
		}

		//m_nDepthFrameIdx = 0;
		//m_nColorFrameIdx = 0;
		//m_nSkeletonFrameIdx = 0;

		//Use only one frame index, make depth/skeleton/color consistent (have same frame index/number)
		m_nTotalFrameIdx = 0;

		SendDlgItemMessage( m_hWnd, IDC_BTN_CTRL, WM_SETTEXT, 0,  (LPARAM)(TEXT("Stop")) );

		//Disable data type selection
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_COLOR), FALSE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_SKELETON), FALSE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_DEPTH), FALSE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_SENSOR), FALSE);

		if (m_bRecordDepth == true) 
		{
			//Allocate buffer to store Depth data
			if (m_pDepthBufferHead2 == NULL) 
			{
				m_pDepthBuffer = new USHORT[DEPTH_IMAGE_WIDTH*DEPTH_IMAGE_HEIGHT*DEPTH_MAX_FRAME_COUNT];
				m_pDepthBufferHead1 = m_pDepthBuffer;
				m_pDepthBufferHead2 = m_pDepthBuffer;
			}

			//If Color or Skeleton should also be saved, Depth should be store temperately in buffer for synchronization
			//The actual STORING will be processed when saving Skeleton or Color
			//The actual SAVING will be processed when pressed "Stop" button
			if (m_bRecordSkeleton == true || m_bRecordColor == true)
			{
				m_pTmpDepthBuffer = new USHORT[DEPTH_IMAGE_WIDTH*DEPTH_IMAGE_HEIGHT];
			}

			//Open file to save timestamp for depth in a separate file
			ostringstream strFilePathName;
			strFilePathName << m_strDataFolder.c_str() << "\\" << DEPTH_TIMESTAMP_FILE;
			string tmpStr = strFilePathName.str();

			m_fpDepthTimestamp = fopen(tmpStr.c_str(), "wt");

			if (m_fpDepthTimestamp == NULL) 
			{
				MessageBox(NULL,TEXT("Failed to save timestamp for depth data"), TEXT("Depth Data"), MB_OK | MB_ICONHAND);
				return;
			}

		}

		//If both Color and Skeleton should be saved, save Color temperately in buffer for synchronization
		if (m_bRecordColor == true && m_bRecordSkeleton == true)
		{
			m_pTmpColorBuffer = new BYTE[COLOR_IMAGE_WIDTH*COLOR_IMAGE_HEIGHT*COLOR_IMAGE_BYTE_PER_PIXEL];
		}


		if (m_bRecordSensor == true) 
		{
			//Open file to save sensor data
			ostringstream strFilePathName;
			strFilePathName << m_strDataFolder.c_str() << "\\Sensor.txt";
			string tmpStr = strFilePathName.str();

			m_fpSensor = fopen(tmpStr.c_str(), "wt");

			if (m_fpSensor == NULL) 
			{
				MessageBox(NULL,TEXT("Failed to save sensor data"), TEXT("Sensor Data"), MB_OK | MB_ICONHAND);
				return;
			}

		}

		if ( m_bRecordSkeleton == true || m_bRecordDepth == true || m_bRecordColor == true || m_bRecordSensor == true)
		{
			SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_STATUS),TEXT("Tracking and Recording..."));
		}

		m_hThSeparateDrawProcess = CreateThread(NULL, 0, SeparateDraw_ProcessThread, this, 0, NULL);

		m_bStarted = true;

	} 
	else 
	{
		//User pressed the "Stop" button
		m_bStarted = false;

		//Write Depth data into files when "Stop" is pressed (for performance concern)
		if (m_bRecordDepth == true && m_pDepthBufferHead2 != NULL) 
		{
			SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_STATUS),TEXT("Saving..."));
			SaveDepthData();
		}

		if (m_pDepthBufferHead2 != NULL) 
		{
			delete []m_pDepthBufferHead2;
			m_pDepthBufferHead2 = NULL;

			m_pDepthBufferHead1 = NULL;
			m_pDepthBuffer = NULL;
		}

		if (m_pTmpDepthBuffer != NULL)
		{
			delete []m_pTmpDepthBuffer;
			m_pTmpDepthBuffer = NULL;
		}

		if (m_pTmpColorBuffer != NULL)
		{
			delete []m_pTmpColorBuffer;
			m_pTmpColorBuffer = NULL;
		}
		
		//Close depth timestamp file
		if (m_fpDepthTimestamp != NULL)
		{
			fclose(m_fpDepthTimestamp);
			m_fpDepthTimestamp = NULL;
		}

		//Close sensor file
		if (m_fpSensor != NULL)
		{
			fclose(m_fpSensor);
			m_fpSensor = NULL;
		}

		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_STATUS), TEXT("Stopped"));

		//Enable data type selection
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_COLOR), TRUE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_SKELETON), TRUE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_DEPTH), TRUE);
		EnableWindow(GetDlgItem(m_hWnd, IDC_CHK_SENSOR), TRUE);

		//Clear position display
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_POS_X),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_POS_Y),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_POS_Z),TEXT(""));

		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_ACCL_X),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_ACCL_Y),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_ACCL_Z),TEXT(""));

		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_GYRO_X),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_GYRO_Y),TEXT(""));
		SetWindowText(GetDlgItem(m_hWnd, IDC_STATIC_GYRO_Z),TEXT(""));

		SendDlgItemMessage( m_hWnd, IDC_BTN_CTRL, WM_SETTEXT, 0,  (LPARAM)(TEXT("Start")) );

	}

}

//Save Depth info into files
void CSkeletalViewerApp::SaveDepthData()
{
	int i,j;
	USHORT uDepthValue;

	return;

	for (i = 1; i <= m_nTotalFrameIdx; i++)
	{
		ostringstream strFilePathName;
		strFilePathName << m_strDataFolder.c_str() << "\\Dpt_" << i << ".txt";
		string tmpStr = strFilePathName.str();

		FILE* fp = fopen(tmpStr.c_str(), "wt");

		if (fp == NULL) 
		{
			MessageBox(NULL,TEXT("Failed to save Depth data"), TEXT("Depth Data"), MB_OK | MB_ICONHAND);
			return;
		}

		for (j = 0; j < DEPTH_IMAGE_WIDTH * DEPTH_IMAGE_HEIGHT; j++)
		{
			uDepthValue = (m_pDepthBufferHead1[j] & 0xFFF8) >> 3;
			fprintf(fp, "%u ", uDepthValue);
		}

		fclose(fp);

		m_pDepthBufferHead1 = m_pDepthBufferHead1 + DEPTH_IMAGE_WIDTH * DEPTH_IMAGE_HEIGHT;
	}

}

//Get Local IP Address
string CSkeletalViewerApp::getLocalIP()
{
	ostringstream ostrIP;
	char hostname[200];
	HOSTENT *h;
	WORD   wVersionRequested; 
	WSADATA   wsaData; 

	wVersionRequested = MAKEWORD(2,2); 

	if  (WSAStartup(wVersionRequested, &wsaData) == 0)
	{ 

		ZeroMemory(hostname, sizeof(hostname));

		if (gethostname(hostname, sizeof(hostname)) == SOCKET_ERROR)
		{
			return "";
		}

		h = gethostbyname(hostname);
		if (h == NULL)
		{
			return "";
		}

		long a[4];
		for (int i=0; i<4; i++)
		{
			a[i] = h->h_addr_list[0][i];
			if (a[i] < 0) a[i] += 256;
		}

		WSACleanup(   ); 

		ostrIP << a[0] << "." << a[1] << "." << a[2] << "." << a[3];

		return ostrIP.str();
	} 
	else
	{
		return "";
	}

}




