package org.wso2.health.tool.utils;

import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wso2.health.tool.ClientConnectorGenException;
import org.wso2.health.tool.Resource;
import org.wso2.health.tool.SearchParam;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

public class ClientConnectorGenUtils {

    private static final Log log = LogFactory.getLog(ClientConnectorGenUtils.class);

    public static void handleException(String message, Throwable throwable) throws ClientConnectorGenException {

        log.error(message);
        throw new ClientConnectorGenException(message, throwable);
    }

    public static void handleException(String message) throws ClientConnectorGenException {

        log.error(message);
        throw new ClientConnectorGenException(message);
    }

    public static String toStartCase(String name, boolean splitByCamelCase) {

        String str = name;
        str = str.replaceAll(":", "");
        if (splitByCamelCase) {
            // split the string by type camel case if it's not the name of the resource
            str = StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(str), ' ');
        }

        str = str.replaceAll("[,.\\-_]", " ");
        str = str.replaceAll("[&']", "");
        str = str.replaceAll("\\s+", " ");
        str = WordUtils.capitalize(str);

        if (str.startsWith(" ")) {
            str = str.substring(1);
        }
        return str;
    }

    public static String toLowerCamelCase(String name) {

        String str = toStartCase(name, false);
        str = str.replaceAll("\\s+", "");
        str = UPPER_CAMEL.to(LOWER_CAMEL, str);
        return str;
    }

    public static SearchParam setSearchParam(String name, String type, String suffix) {

        SearchParam searchParam = new SearchParam(name, type);
        searchParam.setParameterName(ClientConnectorGenUtils.toLowerCamelCase(name + suffix));
        searchParam.setDisplayName(ClientConnectorGenUtils.toStartCase(name + suffix, true));
        return searchParam;
    }

    public static Resource setResource(String name, List<SearchParam> searchParams, Map<String, Boolean> functionsMap) {

        Resource resource = new Resource(name, searchParams,functionsMap);
        return resource;
    }

    public static VelocityContext readConnectorProperties(VelocityContext context, Map<String, String> connector) throws Exception {

        String connectorName =  connector.get("connectorName");

        if (StringUtils.isEmpty(connectorName)) {
            ClientConnectorGenUtils.handleException("The \"connectorName\" value is missing. Provide the name for the connector.");
        }

        JSONObject capabilityStmtObject;
        String capabilityStatementPath = connector.get("capabilityStatementPath");
        String baseUrl = connector.get("baseURL");

        if (StringUtils.isEmpty(capabilityStatementPath)) {
            log.info("The \"capabilityStatementPath\" value is missing. Therefore the capability statement is being" +
                    " retrieved using the base URL.");

            if (StringUtils.isEmpty(baseUrl)) {
                ClientConnectorGenUtils.handleException("The \"baseURL\" value is missing. Provide the base URL for the connector.");
            }
            capabilityStmtObject = ClientConnectorGenUtils.getCapabilityStatement(baseUrl);

        } else {
            FileReader reader = new FileReader(capabilityStatementPath);
            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(reader);
            capabilityStmtObject = (JSONObject) obj;
            context.put("capabilityStatementPath", capabilityStatementPath);
        }
        String auth =  connector.get("auth");
        if (StringUtils.isEmpty(auth)) {
            ClientConnectorGenUtils.handleException("The \"auth\" value is missing. Provide the authentication method for the connector.");
        }
        auth = auth.toLowerCase();
        if (!(auth.equals("pkjwt") || auth.equals("oauth2") || auth.equals("none") || auth.equals("basic"))) {
            ClientConnectorGenUtils.handleException("The \"auth\" value is invalid. Provide a valid authentication method.");
        }

        String acceptHeader = "application/fhir+json";

        context.put("auth", auth);
        context.put("acceptHeader", acceptHeader);
        context.put("connectorName", connectorName);
        context.put("capabilityStmtObject", capabilityStmtObject);
        context.put("baseURL", baseUrl);
        return context;
    }

    public static JSONObject getCapabilityStatement(String baseUrl) throws Exception {

        String urlString = baseUrl;
        if (urlString.endsWith("/")) {
            urlString += "metadata";
        } else {
            urlString += "/metadata";
        }

        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");

        int responseStatus = con.getResponseCode();
        if (responseStatus == HttpURLConnection.HTTP_OK) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(sb.toString());

            bufferedReader.close();
            con.disconnect();
            return json;

        } else {

            String message =
                    "Error occurred while retrieving the capability statement. Response: " + "[Status : " + responseStatus + " " + "Message: " +
                            con.getResponseMessage() + "]";
            con.disconnect();
            log.error(message);
            throw new Exception(message);
        }
    }
}
