#include "StdAfx.h"
#include "ImageProcessor.h"
#include <windows.h>
#include <stdio.h>

#define BM_HEAD_TYPE	(WORD)(('M' << 8) | 'B')	// Type is 'MB'


ImageProcessor::ImageProcessor(void)
{
}


ImageProcessor::~ImageProcessor(void)
{
}


// Convert the image from upsidedown to normal view
BYTE* ImageProcessor::ConvertUpsideDownImage(BYTE* pBuffer, int width, int height,  int bytePerPixel)
{
	// first make sure the parameters are valid
	if ( (pBuffer == NULL) || (width == 0) || (height == 0) )  return NULL;
	
	// we can already store the size of the new padded buffer
	long lNumOfLineBytes = bytePerPixel * width;
	DWORD dwTotalBits = lNumOfLineBytes * height;

	// create new buffer
	BYTE* newbuf = new BYTE[dwTotalBits];
	
	// fill the buffer with zero bytes then we dont have to add
	// extra padding zero bytes later on
	memset ( newbuf, 0, dwTotalBits );

	BYTE* tmpLineBuf = new BYTE[lNumOfLineBytes];
	memset(tmpLineBuf, 0x00, lNumOfLineBytes);

	BYTE* tmpBuf = newbuf;

	for ( int y = 0; y < height; y++ )
	{
		memcpy(tmpLineBuf, pBuffer + y*lNumOfLineBytes, lNumOfLineBytes);
		memcpy(newbuf + (height-1-y) * lNumOfLineBytes, tmpLineBuf, lNumOfLineBytes);
	}

	delete []tmpLineBuf;
	tmpLineBuf = NULL;

	return newbuf;
}

// Save the image data as image file
bool ImageProcessor::SaveImage(BYTE* pBuffer, LPCTSTR imageFile, int width, int height, int bytePerPixel)
{
	// declare bmp structures 
	BITMAPFILEHEADER bmfh;
	BITMAPINFOHEADER bminfo;

	return true;
	
	// first make sure the parameters are valid
	if ((pBuffer == NULL) || (imageFile == NULL) || (width == 0) || (height == 0) || (bytePerPixel == 0))
		return false;

	// andinitialize them to zero
	memset(&bmfh, 0x00, sizeof(BITMAPFILEHEADER));
	memset(&bminfo, 0x00, sizeof(BITMAPINFOHEADER));
	
	// fill the fileheader with data
	bmfh.bfType = BM_HEAD_TYPE;
	bmfh.bfReserved1 = 0;
	bmfh.bfReserved2 = 0;

	long lNumOfLineBytes = bytePerPixel * width;
	DWORD dwTotalBits = lNumOfLineBytes * height;

	bmfh.bfSize = sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER) + dwTotalBits;
	bmfh.bfOffBits = sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER);		// number of bytes to start of bitmap bits
	
	// fill the infoheader
	bminfo.biSize = sizeof(BITMAPINFOHEADER);
	bminfo.biWidth = width;
	bminfo.biHeight = height;
	bminfo.biPlanes = 1;		// we only have one bitplane
	bminfo.biBitCount = bytePerPixel*8;		// 32 bits
	bminfo.biCompression = BI_RGB;	
	bminfo.biSizeImage = dwTotalBits;		
	bminfo.biXPelsPerMeter = 0;     // paint and PSP use this values
	bminfo.biYPelsPerMeter = 0;     
	bminfo.biClrUsed = 0;			// we are in RGB mode and have no palette
	bminfo.biClrImportant = 0;    // all colors are important

	try
	{
		// now we open the file to write to
		HANDLE file = CreateFile( imageFile , GENERIC_WRITE, FILE_SHARE_READ, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL );
		if ( file == NULL )
		{
			CloseHandle( file );
			return false;
		}
	
		// write file header
		unsigned long bwritten;
		if ( WriteFile( file, &bmfh, sizeof ( BITMAPFILEHEADER ), &bwritten, NULL ) == false )
		{	
			CloseHandle( file );
			return false;
		}

		// write info header
		if ( WriteFile( file, &bminfo, sizeof ( BITMAPINFOHEADER ), &bwritten, NULL ) == false )
		{	
			CloseHandle( file );
			return false;
		}

		// write image data
//		if ( WriteFile ( file, pBuffer, dwTotalBits, &bwritten, NULL ) == false )

		//The original image data is upsidedown to the image shown on the GUI, convert it to the same view
		if ( WriteFile( file, ConvertUpsideDownImage(pBuffer, width, height,bytePerPixel), dwTotalBits, &bwritten, NULL ) == false )
		{	
			CloseHandle( file );
			return false;
		}
	
		// and close file
		CloseHandle( file );
	}
	catch (...)
	{
		::MessageBoxA(NULL, "Failed to save image!", "Save Image", MB_OK | MB_ICONHAND);
		return false;
	}

	return true;
}



