package com.psddev.cms.tool.file;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import com.psddev.cms.tool.FileContentType;
import com.psddev.cms.tool.PageWriter;
import com.psddev.cms.tool.ToolPageContext;
import com.psddev.dari.util.StorageItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(Enclosed.class)
public class VideoFileTypeTest {

    @RunWith(MockitoJUnitRunner.class)
    public static class PriorityTest {

        VideoFileType videoFileType;

        @Mock
        StorageItem storageItem;

        @Before
        public void before() {
            videoFileType = new VideoFileType();
        }

        @Test
        public void defaultPriorityTest() {
            when(storageItem.getContentType()).thenReturn("video/mp4");
            assertTrue(videoFileType.getPriority(storageItem) == FileContentType.DEFAULT_PRIORITY_LEVEL);
        }

        @Test
        public void negativePriorityTest() {
            when(storageItem.getContentType()).thenReturn("image/png");
            assertTrue(videoFileType.getPriority(storageItem) < FileContentType.DEFAULT_PRIORITY_LEVEL);
        }
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class PreviewTest {

        VideoFileType videoFileType;

        @Mock
        StorageItem storageItem;

        @Before
        public void before() {
            videoFileType = new VideoFileType();
            when(storageItem.getContentType()).thenReturn("video/mp4");
        }

        @Test
        public void validatePreview() throws IOException, ServletException {
            ToolPageContext page = spy(new ToolPageContext((ServletContext) null, null, null));
            StringWriter writer = new StringWriter();
            doReturn(new PrintWriter(writer)).when(page).getDelegate();

            String path = "/video/file.mp4";

            doAnswer(invocation -> "test").when(page).localize(any(), any());
            when(storageItem.getPublicUrl()).thenReturn(path);

            videoFileType.writePreview(page, null, storageItem);

            // validate html output
            Document doc = Jsoup.parse(writer.toString());

            Element video = doc.select("video").first();
            assertNotNull(video);

            Element link = doc.select("a").first();
            Element source = video.select("source").first();

            assertEquals(source.attr("src"), path);
            assertEquals(link.attr("href"), path);
        }
    }
}