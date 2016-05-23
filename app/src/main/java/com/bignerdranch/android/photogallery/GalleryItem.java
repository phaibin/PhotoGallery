package com.bignerdranch.android.photogallery;

import com.google.gson.annotations.SerializedName;

/**
 * Created by leon on 5/23/16.
 */
public class GalleryItem {
    @SerializedName("title")
    private String mCaption;
    @SerializedName("id")
    private String mId;
    @SerializedName("url_s")
    private String mUrl;

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public String getId() {

        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getCaption() {

        return mCaption;
    }

    public void setCaption(String caption) {
        mCaption = caption;
    }

    @Override
    public String toString() {
        return mCaption;
    }
}
