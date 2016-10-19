using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

using Microsoft.Kinect;

namespace ConsoleApplication1
{
    class Program
    {
        static void Main(string[] args)
        {
            //初始化sensor实例
            KinectSensor sensor = KinectSensor.KinectSensors[0];

            //初始化照相机
            sensor.DepthStream.Enable();
            sensor.DepthFrameReady += new EventHandler<DepthImageFrameReadyEventArgs>(sensor_DepthFrameReady);

            Console.ForegroundColor = ConsoleColor.Green;

            //打开数据流
            sensor.Start();

            while (Console.ReadKey().Key != ConsoleKey.Spacebar)
            {

            }

        }

        static void sensor_DepthFrameReady(object sender, DepthImageFrameReadyEventArgs e)
        {
            using (var depthFrame = e.OpenDepthImageFrame())
            {
                if (depthFrame == null) return;
                short[] bits = new short[depthFrame.PixelDataLength];
                depthFrame.CopyPixelDataTo(bits);
                foreach (var bit in bits)
                    Console.Write(bit);
         
                Console.WriteLine("\n");
               // Console.WriteLine();
            }
        }

    }
}
