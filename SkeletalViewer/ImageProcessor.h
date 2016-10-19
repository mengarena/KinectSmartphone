#pragma once
class ImageProcessor
{
public:
	ImageProcessor(void);
	~ImageProcessor(void);

	static bool SaveImage(BYTE* pBuffer, LPCTSTR imageFile, int width, int height, int bytePerPixel);

	static BYTE* ConvertUpsideDownImage(BYTE* pBuffer, int width, int height,  int bytePerPixel);
};
