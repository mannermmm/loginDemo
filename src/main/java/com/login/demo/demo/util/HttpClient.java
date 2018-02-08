package com.login.demo.demo.util;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import java.util.Map;


public interface HttpClient {

    String httpGetJson(String requestUrl);

    HttpResponse httpGetJsontest1(String requestUrl);

    HttpResponse httpPost1(String requestUrl, Map<String, String> data, Header[] headers);
}
