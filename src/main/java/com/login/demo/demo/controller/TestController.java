package com.login.demo.demo.controller;

import com.login.demo.demo.util.AsyncHttpClient;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.ImageHelper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import org.springframework.web.bind.annotation.*;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@RestController
@EnableAutoConfiguration

@RequestMapping("/test")
public class TestController {

    @Autowired
    private AsyncHttpClient httpClient;



    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login() {
        Map<String, String> param = new HashMap<>();
        HttpResponse url = httpClient.httpGetJsontest1("https://auth.dxy.cn/accounts/login?service=http%3A%2F%2Fwww.dxy.cn%2Fuser%2Findex.do%3Fdone%3Dhttp%3A%2F%2Fwww.dxy.cn%2F&qr=false&method=1");
        Header[] headers = url.getHeaders("Set-Cookie");
        ITesseract iTesseract = new Tesseract();
        HttpResponse rest = httpClient.httpGetJsontest1("https://auth.dxy.cn/jcaptcha");
        String test = httpClient.getDataFromResponse("", url);
        Document doc = Jsoup.parse(test);
        Elements e1 = doc.select("input");
        String nlt = "";
        for (Element element : e1) {
            String ss = element.toString();
            if (ss.matches(".*nlt.*")) {
                nlt = ss.substring(ss.lastIndexOf("value") + 7, ss.lastIndexOf(">") - 1);
            }
        }
        HttpEntity httpEntity = rest.getEntity();
        try {
            InputStream in = httpEntity.getContent();
            //得到图片的二进制数据，以二进制封装得到数据，具有通用性
            byte[] data = readInputStream(in);
            //new一个文件对象用来保存图片，默认保存当前工程根目录
            File imageFile = new File("/Users/wenyouli/workspace/accounts/tessdata/test.jpg");
            //创建输出流
            FileOutputStream outStream = new FileOutputStream(imageFile);
            //写入数据
            outStream.write(data);
            //关闭输出流
            outStream.close();
            BufferedImage image = ImageIO.read(imageFile);
            int col = image.getWidth();
            BufferedImage textImage = ImageHelper.convertImageToGrayscale(ImageHelper.getSubImage(image, 0, 0, 150, 60));
            textImage = ImageHelper.convertImageToBinary(textImage);
            textImage = ImageHelper.getScaledInstance(textImage, textImage.getWidth() * 3, textImage.getHeight() * 2);

            textImage = ImageHelper.convertImageToBinary(textImage);

            String result = iTesseract.doOCR(textImage);
            result = result.replaceAll("\n\n", "");
            param.put("validateCode", result);
            param.put("loginType", "1");
            param.put("keepOnlineType", "2");
            param.put("username", "15811576422");
            param.put("password", "a12345678a");
            param.put("trys", "0");
            param.put("nlt", nlt);
            param.put("_eventId", "submit");
            HttpResponse resss = httpClient.httpPost1("https://auth.dxy.cn/accounts/login?service=http%3A%2F%2Fwww.dxy.cn%2Fuser%2Findex.do%3Fdone%3Dhttp%3A%2F%2Fwww.dxy.cn%2F&qr=false&method=1",param, headers);

            Header[] headers1 = resss.getHeaders("Set-Cookie");

            return httpClient.getDataFromResponse("", resss);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @RequestMapping(value = "/login1", method = RequestMethod.GET)
    public String login(String result) {
        Map<String, String> param = new HashMap<>();
        HttpResponse url = httpClient.httpGetJsontest1("https://auth.dxy.cn/accounts/login?service=http%3A%2F%2Fwww.dxy.cn%2Fuser%2Findex.do%3Fdone%3Dhttp%3A%2F%2Fwww.dxy.cn%2F&qr=false&method=1");
        Header[] headers = url.getHeaders("Set-Cookie");
        ITesseract iTesseract = new Tesseract();
        String test = httpClient.getDataFromResponse("", url);
        Document doc = Jsoup.parse(test);
        Elements e1 = doc.select("input");
        String nlt = "";
        for (Element element : e1) {
            String ss = element.toString();
            if (ss.matches(".*nlt.*")) {
                nlt = ss.substring(ss.lastIndexOf("value") + 7, ss.lastIndexOf(">") - 1);
            }
        }
        try {
            result = result.replaceAll("\n\n", "");
            param.put("validateCode", result);
            param.put("loginType", "1");
            param.put("keepOnlineType", "2");
            param.put("username", "15811576422");
            param.put("password", "a12345678a");
            param.put("trys", "0");
            param.put("nlt", nlt);
            param.put("_eventId", "submit");
            HttpResponse resss = httpClient.httpPost1("https://auth.dxy.cn/accounts/login?service=http%3A%2F%2Fwww.dxy.cn%2Fuser%2Findex.do%3Fdone%3Dhttp%3A%2F%2Fwww.dxy.cn%2F&qr=false&method=1",param, headers);

            Header[] headers1 = resss.getHeaders("Set-Cookie");

            return httpClient.getDataFromResponse("", resss);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @RequestMapping("/a")
    public File mapping() {
        HttpResponse rest = httpClient.httpGetJsontest1("https://auth.dxy.cn/jcaptcha");

        HttpEntity httpEntity = rest.getEntity();
        try {
            InputStream in = httpEntity.getContent();

            //得到图片的二进制数据，以二进制封装得到数据，具有通用性
            byte[] data = readInputStream(in);
            //new一个文件对象用来保存图片，默认保存当前工程根目录
            File imageFile = new File("./tessdata/test.jpg");
            //创建输出流
            FileOutputStream outStream = new FileOutputStream(imageFile);
            //写入数据
            outStream.write(data);
            //关闭输出流
            outStream.close();
            return imageFile;
        } catch (Exception e) {

        }
        return null;
    }

    @RequestMapping("/d")
    public String mappings() {
        return httpClient.httpGetJson("http://www.dxy.cn/webservices/user/userinfo");
    }

    @RequestMapping("/f")
    public String mappingsd() {
        return httpClient.httpGetJson("http://www.dxy.cn/bbs/topic/38305898");
    }

    private static byte[] readInputStream(InputStream inStream) throws Exception{
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while( (len=inStream.read(buffer)) != -1 ){
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }

}
