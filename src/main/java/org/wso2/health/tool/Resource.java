package org.wso2.health.tool;

import java.util.List;
import java.util.Map;

public class Resource {

    private String name;
    private List<SearchParam> searchParams;
    private Map<String, Boolean> functionsMap;

    public Resource(String name, List<SearchParam> searchParams, Map<String, Boolean> functionsMap) {

        this.name = name;
        this.searchParams = searchParams;
        this.functionsMap = functionsMap;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SearchParam> getSearchParams() {
        return searchParams;
    }

    public void setSearchParams(List<SearchParam> searchParams) {
        this.searchParams = searchParams;
    }

    public Map<String, Boolean> getFunctionsMap() {
        return functionsMap;
    }

    public void setFunctionsMap(Map<String, Boolean> functionsMap) {
        this.functionsMap = functionsMap;
    }
}

