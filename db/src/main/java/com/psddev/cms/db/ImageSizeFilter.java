
package com.psddev.cms.db;

import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.util.AbstractFilter;
import com.psddev.dari.util.JavaImageServlet;
import com.psddev.dari.util.RoutingFilter;
import com.psddev.dari.util.StorageItem;
import com.psddev.dari.util.StringUtils;
import com.psddev.dari.util.UuidFormatException;
import com.psddev.dari.util.UuidUtils;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public class ImageSizeFilter extends AbstractFilter {
    protected static final String JAVA_IMAGE_SERVLET_PATH = JavaImageServlet.class.getAnnotation(RoutingFilter.Path.class).value();
    private static final String IMAGE_PATH_ATTRIBUTE = "_Image_Path";

    @Override
    protected void doRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        String path = request.getServletPath();

        String pathInfo = StringUtils.getPathInfo(path, JAVA_IMAGE_SERVLET_PATH);

        if (pathInfo != null && pathInfo.substring(1).contains("/")) {
            //check if path contains image size
            String[] parameters = pathInfo.substring(1).split("/");
            String imageSize = parameters[0];

            StandardImageSize standardImageSize = findStandardImageSize(imageSize);
            if (standardImageSize != null) {
                //String imageId = pathInfo.substring(2 + imageSizeIndex, pathInfo.lastIndexOf("."));
                String field = parameters[1];
                String imageId = parameters[2].substring(0, parameters[2].lastIndexOf("."));
                Integer revision = 0;
                if (imageId.contains("-v")) {
                    int versionIndex = imageId.lastIndexOf("-v");
                    revision = Integer.parseInt(imageId.substring(versionIndex + 2));
                    imageId = imageId.substring(0, versionIndex);
                }

                try {
                    UUID id = UuidUtils.fromString(imageId);
                    ImageTag.Item image = Query.from(ImageTag.Item.class).where("id = ?", id).first();
                    if (image != null) {
                        State state = image.getState();
                        StorageItem storageItem = (StorageItem) state.get(field);

                        if (storageItem != null) {
                            String forwardPath = null;
                            if (image.as(ImageTag.Item.Data.class).getImageFieldPaths().size() > 0) {
                                findForwardPath : for (ImageTag.Item.ImageFieldPath fieldPath : image.as(ImageTag.Item.Data.class).getImageFieldPaths()) {
                                    if (fieldPath.getField().equals(field)) {
                                        for (ImageTag.Item.ImageSizePath imageSizePath : fieldPath.getImageSizePaths()) {
                                            if (imageSizePath.getSize().contains(imageSize)) {
                                                if (imageSizePath.getPaths().size() > revision) {
                                                    forwardPath = imageSizePath.getPaths().get(revision);
                                                    break findForwardPath;
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (!StringUtils.isBlank(forwardPath)) {
                                request.setAttribute(IMAGE_PATH_ATTRIBUTE, forwardPath);
                                HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(request) {
                                    private String newPath;
                                    private String queryString;
                                    private String url;

                                    @Override
                                    public String getServletPath() {
                                        return !StringUtils.isBlank(this.getNewPath()) ? this.getNewPath() : super.getServletPath();
                                    }

                                    @Override
                                    public String getQueryString() {
                                        if (queryString == null) {
                                            queryString = ((String) this.getAttribute(IMAGE_PATH_ATTRIBUTE)).split("\\?")[1];
                                        }
                                        return StringUtils.isBlank(super.getQueryString()) ? queryString : super.getQueryString() + "&" + queryString;
                                    }

                                    @Override
                                    public String getParameter(String string) {
                                       return string != null && string.equals("url") ? getUrl() : super.getParameter(string);
                                    }

                                    public String getNewPath() {
                                        if (newPath == null) {
                                            newPath = ((String) this.getAttribute(IMAGE_PATH_ATTRIBUTE)).split("\\?")[0];
                                        }
                                        return newPath;
                                    }

                                    public String getUrl() {
                                        if (url == null) {
                                            url = this.getNewPath().substring(this.getNewPath().indexOf("?url=") + 5);
                                        }
                                        return url;
                                    }

                                };

                                request = requestWrapper;
                            }
                        }
                    }

                } catch (UuidFormatException ex) {
                    //no valid UUID continue in the chian
                }
            }
        }

        chain.doFilter(request, response);
    }

    public static StandardImageSize findStandardImageSize(String internalSizeName) {
        for (StandardImageSize size : StandardImageSize.findAll()) {
            if (StringUtils.equals(size.getInternalName(), internalSizeName)) {
                return size;
            }
        }
        return null;
    }

    private static StorageItem findStorageItem(State state) {
        ObjectType objectType = state.getType();

        if (objectType != null) {
            for (ObjectField objectField : objectType.getFields()) {
                if (ObjectField.FILE_TYPE.equals(objectField.getInternalType())) {
                    String field = objectField.getInternalName();
                    Object fieldValue = state.get(field);
                    if (fieldValue instanceof StorageItem) {
                        return (StorageItem) fieldValue;
                    }
                }
            }
        }

        return null;
    }

}
