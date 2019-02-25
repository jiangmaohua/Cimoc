package com.hiroshi.cimoc.source;

import android.util.Pair;

import com.hiroshi.cimoc.model.Chapter;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.ImageUrl;
import com.hiroshi.cimoc.model.Source;
import com.hiroshi.cimoc.parser.MangaCategory;
import com.hiroshi.cimoc.parser.MangaParser;
import com.hiroshi.cimoc.parser.NodeIterator;
import com.hiroshi.cimoc.parser.SearchIterator;
import com.hiroshi.cimoc.soup.Node;
import com.hiroshi.cimoc.utils.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by PingZi on 2019/2/25.
 */

public class WuLing extends MangaParser {

    public static final int TYPE = 34;
    public static final String DEFAULT_TITLE = "50漫画";

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    public WuLing(Source source) {
        init(source, new WuLing.Category());
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        String url = "";
        if (page == 1) {
            url = StringUtils.format("https://www.50mh.com/search/?keywords=%s",
                    URLEncoder.encode(keyword, "UTF-8"));
        }
        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        return new NodeIterator(body.list("#w0 > ul > li")) {
            @Override
            protected Comic parse(Node node) {
                Node node_a = node.list("a").get(0);
                String[] urls = node_a.attr("href").split("/");
                String cid = urls[urls.length - 1];
                String title = node_a.attr("title");
                String cover = node_a.attr("img", "src");
                String author = node.text("p.auth");
                return new Comic(TYPE, cid, title, cover, null, author);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = "https://www.50mh.com/manhua/".concat(cid) + "/";
        return new Request.Builder().url(url).build();
    }

    @Override
    public void parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String cover = body.src("div.comic_i_img > img");
        String title = body.attr("div.comic_i_img > img", "alt");
        String updateTime = body.text("span.zj_list_head_dat");
        String update = updateTime.substring(7, updateTime.length() - 2).trim();
        String author = body.text("ul.comic_deCon_liO > li:eq(0) > a");
        String intro = body.text("p.comic_deCon_d");
        boolean status = isFinish(body.text("ul.comic_deCon_liO > li:eq(2) > a"));
        comic.setInfo(title, cover, update, intro, author, status);
    }

    @Override
    public List<Chapter> parseChapter(String html) {
        List<Chapter> list = new LinkedList<>();
        for (Node node : new Node(html).list("#chapter-list-1 > li > a")) {
            String title = node.text("span.list_con_zj");
            String path = node.hrefWithSplit(2);
            list.add(new Chapter(title, path));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("https://www.50mh.com/manhua/%s/%s.html", cid, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html) {
        List<ImageUrl> list = new LinkedList<>();


        String str = StringUtils.match("chapterImages = \\[(.*?)\\]", html, 1);

        if (str != null) {
            try {
                String[] array = str.split(",");

                // 国漫和日漫的地址是不同的，以 "http开头的是国漫，否则是日漫
                if (array[0].startsWith("\"http")) {
                    for (int i = 0; i != array.length; ++i) {
                        String[] ss = array[i].split("\\\\/");
                        String lastStr = ss[7].substring(0, ss[7].length() - 1);
                        String s = ss[4] + "/" + ss[5] + "/" + ss[6] + "/" + lastStr;

                        // http://mhpic.cnmanhua.com/comic/X/邪气凛然/166话GQ/1.jpg-smh.middle
                        list.add(new ImageUrl(i + 1, "http://mhpic.cnmanhua.com/comic/" + s, false));
                    }
                } else {
                    String urlPrev = StringUtils.match("chapterPath = \"(.*?)\"", html, 1);
                    for (int i = 0; i != array.length; ++i) {
                        String s = array[i].substring(1, array[i].length() - 1);

//                  https://res.manhuachi.com/images/comic/65/129127/1550639616Dw_cEsxXDlcxKi-5.jpg
                        list.add(new ImageUrl(i + 1, "https://res.manhuachi.com/" + urlPrev + s, false));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        // 这里表示的是更新时间
        Node body = new Node(html);
        String updateTime = body.text("span.zj_list_head_dat");
        return updateTime.substring(7, updateTime.length() - 2).trim();
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new LinkedList<>();
        Node body = new Node(html);
        for (Node node : body.list("#w0 > ul > li")) {
            Node node_a = node.list("a").get(0);
            String[] urls = node_a.attr("href").split("/");
            String cid = urls[urls.length - 1] + "/";
            String title = node_a.attr("title");
            String cover = node_a.attr("img", "src");
            String author = node.text("p.auth");
            list.add(new Comic(TYPE, cid, title, cover, null, author));
        }
        return list;
    }

    private static class Category extends MangaCategory {

        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        public String getFormat(String... args) {
            return StringUtils.format("https://www.50mh.com/act/?act=list&page=%%d&catid=%s&ajax=1&order=%s",
                    args[CATEGORY_SUBJECT], args[CATEGORY_ORDER]);
        }

        @Override
        protected List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("最近更新", "0"));
            list.add(Pair.create("少年热血", "1"));
            list.add(Pair.create("武侠格斗", "2"));
            list.add(Pair.create("科幻魔幻", "3"));
            list.add(Pair.create("竞技体育", "4"));
            list.add(Pair.create("爆笑喜剧", "5"));
            list.add(Pair.create("侦探推理", "6"));
            list.add(Pair.create("恐怖灵异", "7"));
            list.add(Pair.create("少女爱情", "8"));
            list.add(Pair.create("恋爱生活", "9"));
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新", "3"));
            list.add(Pair.create("发布", "1"));
            list.add(Pair.create("人气", "2"));
            return list;
        }

    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", "https://www.50mh.com");
    }

}
