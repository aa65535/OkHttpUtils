package utils.okhttp.request;

import java.io.File;

import okhttp3.MediaType;
import utils.okhttp.utils.Constants;

public class PostFileBuilder extends OkHttpBuilder<PostFileBuilder> implements Constants {
    File file;
    MediaType mediaType;

    public PostFileBuilder() {
        this.mediaType = MEDIA_TYPE_STREAM;
    }

    PostFileBuilder(PostFileRequest request) {
        super(request);
        this.file = request.file;
        this.mediaType = request.mediaType;
    }

    @Override
    public PostFileRequest build() {
        if (this.file == null)
            throw new NullPointerException("the file can not be null !");
        return new PostFileRequest(this);
    }

    /**
     * 设置待上传的文件
     */
    public OkHttpBuilder file(File file) {
        this.file = file;
        return this;
    }

    /**
     * 设置请求的 {@link MediaType}，默认是 {@code application/octet-stream}
     */
    public OkHttpBuilder mediaType(MediaType mediaType) {
        this.mediaType = mediaType;
        return this;
    }
}