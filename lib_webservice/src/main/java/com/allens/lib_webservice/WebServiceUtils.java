package com.allens.lib_webservice;

import android.support.v4.util.SimpleArrayMap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Message;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Created by Allens on 2016/9/22.
 */

public class WebServiceUtils {
    // 访问的服务器是否由dotNet开发
    public boolean isDotNet = false;
    // 线程池的大小
    private int threadSize = 5;
    // 创建一个可重用固定线程数的线程池，以共享的无界队列方式来运行这些线程
    private ExecutorService threadPool = Executors.newFixedThreadPool(threadSize);
    // 连接响应标示
    public static final int SUCCESS_FLAG = 0;
    public static final int ERROR_FLAG = 1;


    private WebServiceUtils() {

    }

    private static WebServiceUtils webServiceUtils;

    public static WebServiceUtils create() {
        if (webServiceUtils == null) {
            synchronized (WebServiceUtils.class) {
                if (webServiceUtils == null) {
                    webServiceUtils = new WebServiceUtils();
                }
            }
        }
        return webServiceUtils;
    }


    /**
     * 调用WebService接口，此方法只访问过用Java写的服务器
     *
     * @param url             WebService服务器地址
     * @param nameSpace       命名空间
     * @param methodName      WebService的调用方法名
     * @param mapParams       WebService的参数集合，可以为null
     * @param reponseCallBack 服务器响应接口
     */
    public void call(final String url,
                     final String nameSpace,
                     final String methodName,
                     SimpleArrayMap<String, String> mapParams,
                     final Response reponseCallBack) {
        // 1.创建HttpTransportSE对象，传递WebService服务器地址
        final HttpTransportSE transport = new HttpTransportSE(url);
        transport.debug = true;
        // 2.创建SoapObject对象用于传递请求参数
        final SoapObject request = new SoapObject(nameSpace, methodName);
        // 2.1.添加参数也可以不传
        if (mapParams != null) {
            for (int index = 0; index < mapParams.size(); index++) {
                String key = mapParams.keyAt(index);
                String value = mapParams.get(key);
                request.addProperty(key, value);
            }
        }

        // 3.实例化SoapSerializationEnvelope，传入WebService的SOAP协议的版本号
        final SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        envelope.dotNet = isDotNet; // 设置是否调用的是.Net开发的WebService
        envelope.setOutputSoapObject(request);

        // 4.用于子线程与主线程通信的Handler，网络请求成功时会在子线程发送一个消息，然后在主线程上接收
        final Handler responseHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                // 根据消息的arg1值判断调用哪个接口
                if (msg.arg1 == SUCCESS_FLAG)
                    reponseCallBack.onSuccess((SoapObject) msg.obj);
                else
                    reponseCallBack.onError((Exception) msg.obj);
            }

        };

        // 5.提交一个子线程到线程池并在此线种内调用WebService
        if (threadPool == null || threadPool.isShutdown())
            threadPool = Executors.newFixedThreadPool(threadSize);
        threadPool.submit(new Runnable() {

            @Override
            public void run() {
                SoapObject result = null;
                try {
                    // 解决EOFException
                    System.setProperty("http.keepAlive", "false");
                    // 连接服务器
                    transport.call(null, envelope);
                    if (envelope.getResponse() != null) {
                        // 获取服务器响应返回的SoapObject
                        result = (SoapObject) envelope.bodyIn;
                    }
                } catch (IOException e) {
                    // 当call方法的第一个参数为null时会有一定的概念抛IO异常
                    // 因此需要需要捕捉此异常后用命名空间加方法名作为参数重新连接
                    e.printStackTrace();
                    try {
                        transport.call(nameSpace + methodName, envelope);
                        if (envelope.getResponse() != null) {
                            // 获取服务器响应返回的SoapObject
                            result = (SoapObject) envelope.bodyIn;
                        }
                    } catch (Exception e1) {
                        // e1.printStackTrace();
                        responseHandler.sendMessage(responseHandler.obtainMessage(0, ERROR_FLAG, 0, e1));
                    }
                } catch (XmlPullParserException e) {
                    // e.printStackTrace();
                    responseHandler.sendMessage(responseHandler.obtainMessage(0, ERROR_FLAG, 0, e));
                } finally {
                    // 将获取的消息利用Handler发送到主线程
                    responseHandler.sendMessage(responseHandler.obtainMessage(0, SUCCESS_FLAG, 0, result));
                }
            }
        });
    }

    /**
     * 设置线程池的大小
     *
     * @param threadSize
     */
    public void setThreadSize(int threadSize) {
        webServiceUtils.threadSize = threadSize;
        threadPool.shutdownNow();
        threadPool = Executors.newFixedThreadPool(webServiceUtils.threadSize);
    }

    public void setIsDotNet(Boolean isDotNet) {
        webServiceUtils.isDotNet = isDotNet;
    }

    /**
     * 服务器响应接口，在响应后需要回调此接口
     */
    public interface Response {
        void onSuccess(SoapObject result);

        void onError(Exception e);
    }
}
