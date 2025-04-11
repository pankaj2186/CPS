package com.adobedemosystemprogram42.core.services;

import java.util.Map;

public interface AssetService {

    Map<String, String> getOrderedAssets(String path);

    void creatFolder(String folderName, String path);
}
