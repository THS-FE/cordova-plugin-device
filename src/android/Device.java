/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.device;

import java.util.TimeZone;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import android.os.Environment;


public class Device extends CordovaPlugin {
    public static final String TAG = "Device";

    public static String platform;                            // Device OS
    public static String uuid;                                // Device UUID

    private static final String ANDROID_PLATFORM = "Android";
    private static final String AMAZON_PLATFORM = "amazon-fireos";
    private static final String AMAZON_DEVICE = "Amazon";
    private String fileName = "system" + ".ths";

    /**
     * Constructor.
     */
    public Device() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Device.uuid = getUuid();
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getDeviceInfo".equals(action)) {
            JSONObject r = new JSONObject();
            r.put("uuid", Device.uuid);
            r.put("version", this.getOSVersion());
            r.put("platform", this.getPlatform());
            r.put("model", this.getModel());
            r.put("manufacturer", this.getManufacturer());
	        r.put("isVirtual", this.isVirtual());
            String sn = this.getSerialNumber();
            r.put("serial", sn);
            // liuyx 新增获取不到设备序列号时，进行其他途径数据获取（替代方案一ANDROID_ID，方案2本地SD卡随机码缓存，统一添加固定前缀，以便后续数据筛查），注意：如果通过其他途径获取的数据，已经不是设备序列号了，只能是相对唯一标识设备的字符串
            // 获取到的数据ad开头的是，ANDROID_ID； LC开头的是本地生成的一串随机码，缓存SD卡的
            if(sn==null||sn.equals("")||sn.equals("unknown")){
                // 获取AndroidID
                String androidId = Settings.System.getString(
                        cordova.getActivity().getContentResolver(), Settings.Secure.ANDROID_ID);
                if(androidId!=null&&!androidId.equals("")){
                    //result.put("deviceId", "ad"+androidId);
                    r.put("serial", "ad"+androidId);
                }else{
                    // 未获取到AndroidID，进行本地SD获取
                    try {
                        String localDeviceId = readLocalDeviceId();
                        if(localDeviceId==null||localDeviceId.equals("")){
                            String thsId = "LC"+ UUID.randomUUID().toString();
                            saveLocalDeviceId(thsId);
                            r.put("serial", thsId);
                        }else{
                            r.put("serial", localDeviceId);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }


            callbackContext.success(r);
        }
        else {
            return false;
        }
        return true;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Get the OS name.
     *
     * @return
     */
    public String getPlatform() {
        String platform;
        if (isAmazonDevice()) {
            platform = AMAZON_PLATFORM;
        } else {
            platform = ANDROID_PLATFORM;
        }
        return platform;
    }

    /**
     * Get the device's Universally Unique Identifier (UUID).
     *
     * @return
     */
    public String getUuid() {
        String uuid = Settings.Secure.getString(this.cordova.getActivity().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        return uuid;
    }

    public String getModel() {
        String model = android.os.Build.MODEL;
        return model;
    }

    public String getProductName() {
        String productname = android.os.Build.PRODUCT;
        return productname;
    }

    public String getManufacturer() {
        String manufacturer = android.os.Build.MANUFACTURER;
        return manufacturer;
    }

    public String getSerialNumber() {
        String serial = android.os.Build.SERIAL;
        return serial;
    }

    /**
     * Get the OS version.
     *
     * @return
     */
    public String getOSVersion() {
        String osversion = android.os.Build.VERSION.RELEASE;
        return osversion;
    }

    public String getSDKVersion() {
        @SuppressWarnings("deprecation")
        String sdkversion = android.os.Build.VERSION.SDK;
        return sdkversion;
    }

    public String getTimeZoneID() {
        TimeZone tz = TimeZone.getDefault();
        return (tz.getID());
    }

    /**
     * Function to check if the device is manufactured by Amazon
     *
     * @return
     */
    public boolean isAmazonDevice() {
        if (android.os.Build.MANUFACTURER.equals(AMAZON_DEVICE)) {
            return true;
        }
        return false;
    }

    public boolean isVirtual() {
	return android.os.Build.FINGERPRINT.contains("generic") ||
	    android.os.Build.PRODUCT.contains("sdk");
    }

/**
     * 保存本地的设备ID
     * @param localDeviceId 设备ID
     * @throws IOException
     */
    public  void saveLocalDeviceId(String localDeviceId) throws IOException {
        // 创建目录
        //获取内部存储状态
        String state = Environment.getExternalStorageState();
        //如果状态不是mounted，无法读写
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return;
        }
        String sdCardDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        File appDir = new File(sdCardDir, "CaChe");
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        File file = new File(appDir, fileName);
        if (!file.exists()) {
            file.createNewFile();
        }
        //保存android唯一表示符
        try {
            FileWriter fw = new FileWriter(file);
            fw.write(localDeviceId);
            fw.flush();
            fw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取本地存储的设备ID
     * @return　本地存储的设备ID
     * @throws IOException
     */
    public  String readLocalDeviceId() throws IOException {

        // 创建目录
        //获取内部存储状态
        String state = Environment.getExternalStorageState();
        //如果状态不是mounted，无法读写
        if (!state.equals(Environment.MEDIA_MOUNTED)) {
            return null;
        }
        String sdCardDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        File appDir = new File(sdCardDir, "CaChe");
        if (!appDir.exists()) {
            appDir.mkdir();
        }

        File file = new File(appDir, fileName);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedReader reader = null;
        StringBuilder content=null;
        try {
            FileReader fr = new FileReader(file);
            content= new StringBuilder();
            reader = new BufferedReader(fr);
            String line;
            while ((line= reader.readLine())!=null){
                content.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (reader!=null){
                try {
                    reader.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }

        return content.toString();
    }
}

