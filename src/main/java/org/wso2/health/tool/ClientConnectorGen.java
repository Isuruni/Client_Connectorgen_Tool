package org.wso2.health.tool;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.NullLogChute;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.wso2.health.tool.utils.ClientConnectorGenUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ClientConnectorGen {

    private static final Log log = LogFactory.getLog(ClientConnectorGen.class);
    static VelocityContext context = new VelocityContext();
    private final VelocityEngine velocityEngine;
    private String pathToConnectorDir;
    
    public ClientConnectorGen() throws Exception {

        this.velocityEngine = new VelocityEngine();
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "class");
        properties.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.setProperty("runtime.log.logsystem.class", NullLogChute.class.getName());
        velocityEngine.init(properties);
    }

    void createConnectorDirectory(String connectorName, String outputPath) throws IOException {

        if (outputPath == null) { 
            pathToConnectorDir = connectorName + "Connector";
        } else {
            pathToConnectorDir = outputPath + "/" + connectorName + "Connector";
        }

        Files.createDirectories(Paths.get(pathToConnectorDir));
    }

    private void mergeVelocityTemplate(String outputFile, String template) throws Exception {

        File file = new File(outputFile);
        Writer writer = new FileWriter(file);
        velocityEngine.mergeTemplate(template, "UTF-8",context, writer);
        writer.flush();
        writer.close();
    }

    private void generateComponentFiles() throws Exception {

        String outputFile = pathToConnectorDir + "/Ballerina.toml";
        String template = "templates/fhir-connector/Ballerina_toml_template.vm";
        mergeVelocityTemplate(outputFile, template);

        outputFile = pathToConnectorDir + "/fhir_connector_records.bal";
        template = "templates/fhir-connector/fhir_connector_records_template.vm";
        mergeVelocityTemplate(outputFile, template);

        outputFile = pathToConnectorDir + "/fhir_connector.bal";
        template = "templates/fhir-connector/fhir_connector_template.vm";
        mergeVelocityTemplate(outputFile, template);

        outputFile = pathToConnectorDir + "/Package.md";
        template = "templates/fhir-connector/package_md_template.vm";
        mergeVelocityTemplate(outputFile, template);

    }

    void readCapabilityStatement(JSONObject capabilityStatement) throws Exception {

        String typeObj = (String) capabilityStatement.get("resourceType");
        JSONObject restObject;

        if ("bundle".equalsIgnoreCase(typeObj)) {
            JSONObject entryObject = (JSONObject) (((JSONArray) capabilityStatement.get("entry")).get(0));
            JSONObject resObject = (JSONObject) entryObject.get("resource");
            String resType = (String) resObject.get("resourceType");
            if ("conformance".equalsIgnoreCase(resType) || "capabilitystatement".equalsIgnoreCase(resType)) {
                restObject = (JSONObject) (((JSONArray) resObject.get("rest")).get(0));
            } else {
                throw new Exception("Cannot find the Capability Statement");
            }
        } else {
            restObject = (JSONObject) (((JSONArray) capabilityStatement.get("rest")).get(0));
        }

        JSONArray resourcesArray = (JSONArray) restObject.get("resource");

        List<Resource> resourceList = new ArrayList<>();
        Map<String, String> parameterNameMap = new HashMap<>();

        for (Object object : resourcesArray) {
            Map<String, Boolean> functionsMap = new HashMap<>();
            functionsMap.put("create", false);
            functionsMap.put("read", false);
            functionsMap.put("update", false);
            functionsMap.put("delete", false);
            functionsMap.put("patch", false);
            functionsMap.put("search-type", false);

            JSONObject resourceObject = (JSONObject) object;
            String resourceName = (String) resourceObject.get("type");
            JSONArray interactionArray = (JSONArray) resourceObject.get("interaction");
            JSONArray searchParamArray = (JSONArray) resourceObject.get("searchParam");

            boolean exists = functionsMap.containsValue(false);
            if (exists && interactionArray != null) {
                for (Object interactionObject : interactionArray) {
                    JSONObject interaction = (JSONObject) interactionObject;
                    String functionName = (String) interaction.get("code");
                    Boolean functionFlag = functionsMap.get(functionName);

                    if (functionFlag != null && !functionFlag) {
                        functionsMap.put(functionName, true);
                    }
                }
            }
            List<SearchParam> searchParams = new ArrayList<>();
            if (searchParamArray != null) {

                for (Object param : searchParamArray) {
                    JSONObject paramObject = (JSONObject) param;
                    String paramName = (String) paramObject.get("name");
                    String paramType = (String) paramObject.get("type");
                    SearchParam searchParam = ClientConnectorGenUtils.setSearchParam(paramName, paramType, "");
                    searchParams.add(searchParam);
                    parameterNameMap.put(searchParam.getParameterName(), paramName);
                }
            } else {
                continue;
            }
            //to capture parameters with any modifiers
            SearchParam additionalParam = ClientConnectorGenUtils.setSearchParam("additional-search-parameters", "text", "");
            searchParams.add(additionalParam);
            parameterNameMap.put(additionalParam.getParameterName(), "additionalSearchParameters");

            context.put("resourceName", resourceName);
            context.put("searchParams", searchParams);
            context.put("functionsMap", functionsMap);

            Resource resource = ClientConnectorGenUtils.setResource(resourceName, searchParams,functionsMap);
            resourceList.add(resource);

        }

        resourceList.sort(Comparator.comparing(Resource::getName));
        context.put("resourceList", resourceList);
        context.put("parameterNameMap", parameterNameMap);
        generateComponentFiles();

    }
    
    public void generateConnector(String capabilityStatementPath, String baseURL, String connectorName, String auth, String outputPath) throws Exception {

        try {
            Map<String, String> connector = new HashMap<>();

            connector.put("capabilityStatementPath",capabilityStatementPath);
            connector.put("baseURL", baseURL);
            connector.put("connectorName", connectorName);
            connector.put("auth", auth);
            
            ClientConnectorGen configGenerator = new ClientConnectorGen();

            context = ClientConnectorGenUtils.readConnectorProperties(context, connector);
            
            configGenerator.createConnectorDirectory(connectorName, outputPath);
            
            JSONObject capabilityStmtObject = (JSONObject) context.get("capabilityStmtObject");
            configGenerator.readCapabilityStatement(capabilityStmtObject);

            log.info(connectorName + " connector source project has been successfully generated.");

        } catch (Exception e) {
            String errorMsg = "Error occurred while generating connector files.";
            ClientConnectorGenUtils.handleException(errorMsg, e);
        }
    }
}