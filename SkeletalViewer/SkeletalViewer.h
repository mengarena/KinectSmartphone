//------------------------------------------------------------------------------
// <copyright file="SkeletalViewer.h" company="Microsoft">
//     Copyright (c) Microsoft Corporation.  All rights reserved.
// </copyright>
//------------------------------------------------------------------------------

// Declares of CSkeletalViewerApp class

#pragma once

#include "resource.h"
#include "NuiApi.h"
#include "DrawDevice.h"

// Windows Header Files
#include <windows.h>
#include <ole2.h>

// C RunTime Header Files
#include <stdlib.h>
#include <stdio.h>
#include <malloc.h>
#include <memory.h>
#include <tchar.h>

// Direct2D Header Files
#include <d2d1.h>
#include <d2d1helper.h>

// DirectWrite
#include <dwrite.h>

#include <Winsock2.h>

#pragma comment ( lib, "winmm.lib" )
#pragma comment ( lib, "d2d1.lib" )

#pragma comment ( lib, "Ws2_32.lib" )


#include <string>
using namespace std;

#define SZ_APPDLG_WINDOW_CLASS          _T("SkeletalViewerAppDlgWndClass")
#define WM_USER_UPDATE_FPS              WM_USER
#define WM_USER_UPDATE_COMBO            WM_USER+1
#define WM_USER_UPDATE_TRACKING_COMBO   WM_USER+2

//Add by Meng
// Color image information
#define COLOR_IMAGE_WIDTH			640
#define	COLOR_IMAGE_HEIGHT			480
#define COLOR_IMAGE_BYTE_PER_PIXEL	4

// Depth image information
#define DEPTH_IMAGE_WIDTH		320
#define	DEPTH_IMAGE_HEIGHT		240
#define	DEPTH_MAX_FRAME_COUNT	4000	//Save depth image in buffer up to 4000 frames



class CSkeletalViewerApp
{
public:
    CSkeletalViewerApp();
    ~CSkeletalViewerApp();
    HRESULT                 Nui_Init( );
    HRESULT                 Nui_Init( OLECHAR * instanceName );
    void                    Nui_UnInit( );
    void                    Nui_GotDepthAlert( );
    void                    Nui_GotColorAlert( );
    void                    Nui_GotSkeletonAlert( );

    void                    Nui_Zero();
    void                    Nui_BlankSkeletonScreen( HWND hWnd, bool getDC );
    void                    Nui_DoDoubleBuffer(HWND hWnd,HDC hDC);
    void                    Nui_DrawSkeleton( NUI_SKELETON_DATA * pSkel, HWND hWnd, int WhichSkeletonColor );
    void                    Nui_DrawSkeletonId( NUI_SKELETON_DATA * pSkel, HWND hWnd, int WhichSkeletonColor );

    void                    Nui_DrawSkeletonSegment( NUI_SKELETON_DATA * pSkel, int numJoints, ... );
    void                    Nui_EnableSeatedTracking(bool seated);
    void                    Nui_SetApplicationTracking(bool applicationTracks);
    void                    Nui_SetTrackedSkeletons(int skel1, int skel2);

    RGBQUAD                 Nui_ShortToQuad_Depth( USHORT s );

    static LRESULT CALLBACK MessageRouter(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);
    LRESULT CALLBACK        WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);
    static void CALLBACK    Nui_StatusProcThunk(HRESULT hrStatus, const OLECHAR* instanceName, const OLECHAR* uniqueDeviceName, void* pUserData);
    void CALLBACK           Nui_StatusProc( HRESULT hrStatus, const OLECHAR* instanceName, const OLECHAR* uniqueDeviceName );

    HWND                    m_hWnd;
    HINSTANCE               m_hInstance;

    int MessageBoxResource(UINT nID, UINT nType);

	//Add by Meng
	void					UpdateControlButton();

private:
    void UpdateComboBox();
    void ClearComboBox();

    void UpdateTrackingComboBoxes();
    void UpdateTrackingFromComboBoxes();

    bool                    m_fUpdatingUi;
    TCHAR                   m_szAppTitle[256];    // Application title
    static DWORD WINAPI     Nui_ProcessThread(LPVOID pParam);
    DWORD WINAPI            Nui_ProcessThread();

    // Current kinect
    INuiSensor *            m_pNuiSensor;
    BSTR                    m_instanceId;

    // Draw devices
    DrawDevice *            m_pDrawDepth;
    DrawDevice *            m_pDrawColor;
    ID2D1Factory *          m_pD2DFactory;

    // thread handling
    HANDLE        m_hThNuiProcess;
    HANDLE        m_hEvNuiProcessStop;

    HANDLE        m_hNextDepthFrameEvent;
    HANDLE        m_hNextColorFrameEvent;
    HANDLE        m_hNextSkeletonEvent;
    HANDLE        m_pDepthStreamHandle;
    HANDLE        m_pVideoStreamHandle;
    HFONT         m_hFontFPS;
    HFONT         m_hFontSkeletonId;
    HPEN          m_Pen[NUI_SKELETON_COUNT];
    HDC           m_SkeletonDC;
    HBITMAP       m_SkeletonBMP;
    HGDIOBJ       m_SkeletonOldObj;
    int           m_PensTotal;
    POINT         m_Points[NUI_SKELETON_POSITION_COUNT];
    RGBQUAD       m_rgbWk[640*480];
    DWORD         m_LastSkeletonFoundTime;
    bool          m_bScreenBlanked;
    bool          m_bAppTracking;
    int           m_DepthFramesTotal;
    DWORD         m_LastDepthFPStime;
    int           m_LastDepthFramesTotal;
    DWORD         m_SkeletonIds[NUI_SKELETON_COUNT];
    DWORD         m_TrackedSkeletonIds[NUI_SKELETON_MAX_TRACKED_COUNT];
    ULONG_PTR     m_GdiplusToken;

	//Add by Meng
	bool		  m_bRecordDepth;		//Record Depth info
	bool		  m_bRecordSkeleton;	//Record Skeleton info
	bool		  m_bRecordColor;		//Record Color info
	bool		  m_bStarted;			//Record started or not

	bool		  m_bPhoneConnected;	//Smartphone is connected with PC or not
	bool		  m_bRecordSensor;		//Record Sensor Info

	string		  m_strDataFolder;		//Folder used to store data

//	int			  m_nDepthFrameIdx;			//Index for Depth frame in one-run
//	int			  m_nColorFrameIdx;			//Index for Color frame in one-run
//	int			  m_nSkeletonFrameIdx;		//Index for Skeleton frame in one-run
	int			  m_nTotalFrameIdx;				//Total frame index

	USHORT*		  m_pDepthBuffer;			//For performance concern, depth data should be saved in buffer when running and save later
	USHORT*		  m_pDepthBufferHead1;		//Head of the Depth Buffer(For saving)
	USHORT*		  m_pDepthBufferHead2;		//Head of the Depth Buffer(For releasing)

	HANDLE        m_hThSocketProcess;

	SOCKET		  m_srvSocket;		//Server Socket

	FILE*		  m_fpSensor;		//Sensor file handle
	FILE*		  m_fpDepthTimestamp;	//Depth timestamp file handle

	BYTE*		  m_pTmpColorBuffer;	//Temperately save Color image
	USHORT*		  m_pTmpDepthBuffer;	//Temperately save Depth image

	string		  m_sTmpDepthTimestamp;	//Temperately save timestamp for Depth Image

	void          SaveSkeletonData(NUI_SKELETON_DATA *pSkel, BOOL bPositionOnly);
	void		  ControlKinectRecord();
	void		  SaveDepthData();
	string		  getLocalIP();
    static DWORD WINAPI     Socket_ProcessThread(LPVOID pParam);
    DWORD WINAPI            Socket_ProcessThread();	
	void		  ShowSensorInfo(string sSensorMsg);

	//Special
	static DWORD WINAPI     SeparateDraw_ProcessThread(LPVOID pParam);
    DWORD WINAPI            SeparateDraw_ProcessThread();

	HANDLE        m_hThSeparateDrawProcess;
	bool		  m_bIntracking;
	bool		  m_bBasicSaved;
	NUI_SKELETON_DATA m_basicSkeleton;
	bool		  m_bIDrawed;
	int			  m_nCount1;
	DWORD         m_LastTrackedtime;


};




