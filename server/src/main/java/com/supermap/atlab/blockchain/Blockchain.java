package com.supermap.atlab.blockchain;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.supermap.atlab.storage.Hdfs;
import com.supermap.atlab.utils.Kml;
import com.supermap.atlab.utils.Utils;
import com.supermap.blockchain.sdk.SmChain;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

@Path("/blockchain")
public class Blockchain {

    private File networkFile = new File(this.getClass().getResource("/network-config-testC.yaml").getPath());

    private SmChain smChain;

    public Blockchain() {

        smChain = SmChain.getSmChain("txchannel", networkFile);
        hdfs = new Hdfs();
    }


    Hdfs hdfs;
    Logger logger = Logger.getLogger(this.getClass().getName());

    //    final private File networkConfigFile = new File("/home/cy/Documents/ATL/SuperMap/ATLab-examples/server/src/main/resources/network-config-test.yaml");
//    final private String s3mDirPath = "/home/cy/Documents/ATL/SuperMap/ATLab-examples/server/target/server/s3m/";
    final private String s3mDirPath = "E:\\DemoRecording\\A_SuperMap\\ATLab-examples\\server\\target\\server\\s3m\\";
    final private File networkConfigFile = new File("E:\\DemoRecording\\A_SuperMap\\ATLab-examples\\server\\src\\main\\resources\\network-config-test.yaml");

    //    final private File networkConfigFile = new File(/this.getClass().getResource("/network-config-test.yaml").getPath());
//    final private String chaincodeName = "bimcc";
    final private String chaincodeName = "testCommon";
    //    private ATLChain atlChain;
//    public Blockchain() throws InterruptedException, IOException, URISyntaxException {
//        atlChain = new ATLChain(networkConfigFile);
//        hdfs = new Hdfs();
//    }

    @GET
    public String GetRecord(
            @QueryParam("modelid") String key
    ) {
//        String key = "modelidaa-sidaDa"; // "model002-doorl1";
        String functionName = "GetRecordByKey";

//        String result = atlChain.query(
//                chaincodeName,
//                functionName,
//                new String[]{key}
//        );
        String result = smChain.getSmTransaction().query(
                chaincodeName,
                functionName,
                new String[]{key}
        );

        // 将文件从HDFS缓存到本地
        JSONArray resultJsonArray = JSONArray.parseArray(result);
        resultJsonArray.get(0);
        for (Object o : resultJsonArray) {
            JSONObject jsonObject = (JSONObject) o;
            JSONObject recordJSON = (JSONObject) jsonObject.get("Record");
            JSONArray shashList = (JSONArray) recordJSON.get("SHash");
            for (Object hashObj : shashList) {
                if (!Files.exists(Paths.get(s3mDirPath, hashObj.toString()))) {
                    // TODO 当文件在HDFS中不存在时如何处理？？？
                    String res = hdfs.hdfsDownloadFile(hashObj.toString(), s3mDirPath + hashObj.toString());
                    if (!"Success".equals(res)) {
                        logger.warning("Get file from HDFS failed!!!");
                    }
                }
            }
        }

        return result;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String PutRecord(
            @FormDataParam("modelid") String modelid,
            @FormDataParam("selectStorageMethod") String selectStorageMethod,
            FormDataMultiPart formDataMultiPart
    ) {
        JSONArray jsonArraySHash = new JSONArray();
        JSONArray jsonArrayS3m = new JSONArray();
        List<BodyPart> bodyParts = formDataMultiPart.getBodyParts();
        bodyParts.forEach(o -> {
            String mediaType = o.getMediaType().toString();
            if (!mediaType.equals(MediaType.TEXT_PLAIN)) {
                BodyPartEntity bodyPartEntity = (BodyPartEntity) o.getEntity();
                String fileName = o.getContentDisposition().getFileName();
                if (fileName.contains(".")) {
                    jsonArrayS3m.add(fileName.substring(0, fileName.lastIndexOf('.')));
                } else {
                    jsonArrayS3m.add(fileName);
                }
                InputStream inputStream = bodyPartEntity.getInputStream();
                String hash = null;
                try {
                    hash = Utils.getSHA256(Utils.inputStreamToByteArray(inputStream));
                    jsonArraySHash.add(hash);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                InputStream inLocal = bodyPartEntity.getInputStream();
                // 保存本地文件
                Utils.saveFile(inLocal, s3mDirPath + hash);
                InputStream inHdfs = bodyPartEntity.getInputStream();
                // hdfs存储
                hdfs.hdfsUploadFile(inHdfs, hash);

            }
        });
        JSONArray jsonArray = new JSONArray();
        for(int i = 0; i < jsonArrayS3m.size(); i++){
            JSONArray shashJsonArray = new JSONArray();
            shashJsonArray.add(jsonArraySHash.get(i));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("MID", modelid);
            jsonObject.put("SID", jsonArrayS3m.get(i));
            jsonObject.put("SHash", shashJsonArray);
            jsonArray.add(i, jsonObject);
        }
        // 保存单个模块
        JSONArray jsonToAll = saveSingleS3m(modelid, jsonArray);
        //保存完整模块
        if( selectStorageMethod.equals("appendAndModifyModel") ){
            appendAndModifyModel(modelid, jsonToAll);
        } else if( selectStorageMethod.equals("redefineModel")) {
            redefineModel(modelid, jsonArray);
        }
        return "save file success";
    }

    @Path("/history")
    @GET
    public String GetHistory(
            @QueryParam("key") String key
    ) {
//        String key = "modelidaa-sidaa";
        String functionName = "GetHistoryByKey";

//        String result = atlChain.query(
//                chaincodeName,
//                functionName,
//                new String[]{key}
//        );
        String result = smChain.getSmTransaction().query(
                chaincodeName,
                functionName,
                new String[]{key}
        );

        // 将文件从HDFS缓存到本地
        JSONArray resultJsonArray = JSONArray.parseArray(result);
        resultJsonArray.get(0);
        for (Object o : resultJsonArray) {
            JSONObject jsonObject = (JSONObject) o;
//            System.out.println(jsonObject);
            JSONArray recordJSONArray = (JSONArray) ((JSONObject) jsonObject.get("Record")).get("SHash");
//            jsonObject = (JSONObject) recordJSONArray.get(0);
//            recordJSONArray = (JSONArray) jsonObject.get("SHash");
            for (Object hashObj : recordJSONArray) {
                if (!Files.exists(Paths.get(s3mDirPath, hashObj.toString()))) {
                    // TODO 当文件在HDFS中不存在时如何处理？？？
                    String res = hdfs.hdfsDownloadFile(hashObj.toString(), s3mDirPath + hashObj.toString());
                    if (!"Success".equals(res)) {
                        logger.warning("Get file from HDFS failed!!!");
                    }
                }
            }
        }

        return result;
    }

    @Path("/selector")
    @GET
    public String GetRecordBySelector(
            @QueryParam("modelid") String modelid,
            @QueryParam("s3mid") String s3mid

    ) {
        String selector = "";
        if (s3mid == null) {
            selector = "{\"MID\":\"" + modelid + "\"}";
        } else {
            selector = "{\"MID\":\"" + modelid + "\",\"SID\":\"" + s3mid + "\"}";
        }
        String functionName = "GetRecordBySelector";

//        String result = atlChain.query(
//                chaincodeName,
//                functionName,
//                new String[]{selector}
//        );
        String result = smChain.getSmTransaction().query(
                chaincodeName,
                functionName,
                new String[]{selector}
        );

        // 将文件从HDFS缓存到本地
        JSONArray resultJsonArray = JSONArray.parseArray(result);
        resultJsonArray.get(0);
        for (Object o : resultJsonArray) {
            JSONObject jsonObject = (JSONObject) o;
//            JSONObject recordJSON = (JSONObject) jsonObject.get("Record");
            JSONArray recordJSONArray = (JSONArray) ((JSONObject) jsonObject.get("Record")).get("SHash");
//            JSONArray shashList = (JSONArray) recordJSON.get("SHash");
            for (Object hashObj : recordJSONArray) {
                if (!Files.exists(Paths.get(s3mDirPath, hashObj.toString()))) {
                    // TODO 当文件在HDFS中不存在时如何处理？？？
                    String res = hdfs.hdfsDownloadFile(hashObj.toString(), s3mDirPath + hashObj.toString());
                    if (!"Success".equals(res)) {
                        logger.warning("Get file from HDFS failed!!!");
                    }
                }
            }
        }

        // 直接返回区块链查询结果，具体使用由客户端处理
        return result;
    }

    @DELETE
    public String DelRecord(
            @QueryParam("modelid") String key
    ) {
        String functionName = "DelRecord";

        String result = smChain.getSmTransaction().query(
                chaincodeName,
                functionName,
                new String[]{key}
        );
        return result;
    }

    /**
     * TODO 内部测试 saveSingleS3m && saveALLS3mMoudle && redefineModel
     * @param modelid
     */
    public void storageS3mFile(String modelid) {
        // 单个与整体的存储测试
        JSONArray jsonArrayS3m = Kml.readS3m("modelidaa", "E:\\SuperMapData\\test\\saveTest");
//        System.out.println(jsonArrayS3m);
        JSONArray jsonToAll = saveSingleS3m(modelid, jsonArrayS3m);
        System.out.println(jsonToAll);
        appendAndModifyModel(modelid, jsonToAll);
        redefineModel(modelid, jsonArrayS3m);

        // 单个与整体的删除测试
//        File file = new File("E:\\SuperMapData\\test\\saveTest");
//        String[] fileName = file.list();
//        List<String> deleteList = new ArrayList<>();
//        for(String str : fileName){
//            deleteList.add(str.substring(0, str.lastIndexOf(".")));
//        }
//        JSONArray deleteJson = deleteSingleS3m(modelid, deleteList);
//        deletesALLS3mMoudle(modelid, deleteJson);
    }

    /**
     * 存储单个 s3m 信息，并返回需要修改的值 ---->修改整体存储
     * @param modelid
     * @param jsonArrayS3m
     * @return
     */
    private JSONArray saveSingleS3m(String modelid, JSONArray jsonArrayS3m) {
        JSONArray jsonArray = new JSONArray();
        String getRecord = "GetRecordByKey";
        String putRecord = "PutRecord";
        for (Object object : jsonArrayS3m) {
            JSONObject jsonObject = (JSONObject) object;
            String SID = (String) jsonObject.get("SID");
            JSONArray tmp = (JSONArray) jsonObject.get("SHash");
            String modifySHash = tmp.getString(0);
            String key = modelid + "-" + SID;
            String result = smChain.getSmTransaction().query(
                    chaincodeName,
                    getRecord,
                    new String[]{key}
            );
            if (result.length() != 0) {
                JSONObject jsonResult = JSONObject.parseObject(result);
                tmp = (JSONArray) jsonResult.get("SHash");
                String oldSHash = tmp.getString(0);
                if (!modifySHash.equals(oldSHash)) {
                    String value = jsonObject.toString().replace(oldSHash, modifySHash);
                    String newResult = smChain.getSmTransaction().invoke(
                            chaincodeName,
                            putRecord,
                            new String[]{key, value}
                    );
                    // 将修改之前的 hash 和修改之后的都要传入到整体信息里面
                    JSONObject temp = new JSONObject();
                    temp.put("old", oldSHash);
                    temp.put("modify", modifySHash);
                    jsonArray.add(temp);
                }
            } else {
                String value = jsonObject.toString();
                String newResult = smChain.getSmTransaction().invoke(
                        chaincodeName,
                        putRecord,
                        new String[]{key, value}
                );
                JSONObject temp = new JSONObject();
                temp.put("old", "empty");
                temp.put("modify", modifySHash);
                jsonArray.add(temp);
            }
        }
        JSONObject jsonToAll = new JSONObject();
        jsonToAll.put("SHash", jsonArray);
//        System.out.println(jsonArray);
        return jsonArray;
    }

    /**
     * 修改与追加整体模型信息
     * @param modelid
     * @param jsonArrayS3m
     */
    private void appendAndModifyModel(String modelid, JSONArray jsonArrayS3m) {
        String getRecord = "GetRecord";
        String putRecord = "PutRecord";
        String result = smChain.getSmTransaction().query(
                chaincodeName,
                getRecord,
                new String[]{modelid}
        );
        JSONArray jsonArray = new JSONArray();
        if (result.length() != 0) {
            JSONObject jsonResult = JSONObject.parseObject(result);
            String hash = jsonResult.get("SHash").toString();
            jsonArray = JSONArray.parseArray(hash);
        }
        JSONArray newJsonArray = (JSONArray) jsonArray.clone();
        for (Object json : jsonArrayS3m) {
            JSONObject temp = (JSONObject) JSONObject.parse(json.toString());
            String oldHash = temp.get("old").toString();
            String modifyHash = temp.get("modify").toString();
            // 该信息不为空 即代表之前存储过信息 ----> 分为修改和添加
            if (result.length() != 0) {
                if (oldHash.equals("empty")) {
                    newJsonArray.add(modifyHash);
                } else {
                    newJsonArray.remove(oldHash);
                    newJsonArray.add(modifyHash);
                }
            } else {
                newJsonArray.add(modifyHash);
            }
        }
        if( !jsonArray.equals(newJsonArray) || result.length() == 0){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("SHash", newJsonArray);
            String newValue = jsonObject.toString();
//            System.out.println("newValue" + newValue);
            smChain.getSmTransaction().invoke(
                    chaincodeName,
                    putRecord,
                    new String[]{modelid, newValue}
            );
        }
    }

    /**
     * 重新定义整个模型
     */
    private void redefineModel(String modelid, JSONArray jsonArrayS3m){
        String putRecord = "PutRecord";
        JSONArray jsonArray = new JSONArray();
        for (Object object : jsonArrayS3m) {
            JSONObject jsonObject = (JSONObject) object;
            JSONArray tmp = (JSONArray) jsonObject.get("SHash");
            String SHash = tmp.getString(0);
            jsonArray.add(SHash);
        }
//        System.out.println(jsonArray);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("SHash", jsonArray);
        String newValue = jsonObject.toString();
//        System.out.println("newValue" + newValue);
        smChain.getSmTransaction().invoke(
                chaincodeName,
                putRecord,
                new String[]{modelid, newValue}
        );
    }

    /**
     * 删除单个 s3m 信息
     * @param modelid
     * @param listKey
     * @return
     */
    private JSONArray deleteSingleS3m(String modelid, List<String> listKey) {
        String getRecord = "GetRecord";
        String delRecord = "DelRecord";
        JSONArray deleteJson = new JSONArray();
        String key = null;
        for (String str : listKey) {
            key = modelid + "-" + str;
            String result = smChain.getSmTransaction().query(
                    chaincodeName,
                    getRecord,
                    new String[]{key}
            );
            if (result.length() != 0) {
                JSONObject jsonResult = JSONObject.parseObject(result);
                String tempHash = (String) jsonResult.get("SHash");
                deleteJson.add(tempHash);
            }
//            atlChain.query(
//                    chaincodeName,
//                    delRecord,
//                    new String[]{key}
//            );
        }
        return deleteJson;
    }

    /**
     * 模型整体信息改变
     * @param modelid
     * @param deleteJson
     */
    private void deletesALLS3mMoudle(String modelid, JSONArray deleteJson){
        String getRecord = "GetRecord";
        String result = smChain.getSmTransaction().query(
                chaincodeName,
                getRecord,
                new String[]{modelid}
        );
        JSONObject jsonResult = JSONObject.parseObject(result);
        String hash = jsonResult.get("SHash").toString();
        JSONArray jsonArray = JSONArray.parseArray(hash);
        for(Object o : deleteJson){
            jsonArray.remove(o);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("SHash", jsonArray.toString());
        String newValue = jsonObject.toString();
//        System.out.println(newValue);
//        System.out.println(jsonArray.size());
//        atlChain.query(
//                chaincodeName,
//                putRecord,
//                new String[]{modelid, newValue}
//        );
    }
}
