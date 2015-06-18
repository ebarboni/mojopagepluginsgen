/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mojohaus.plugins.site;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.maven.doxia.macro.AbstractMacro;
import org.codehaus.plexus.component.annotations.Component;
import org.apache.maven.doxia.macro.Macro;
import org.apache.maven.doxia.macro.MacroExecutionException;
import org.apache.maven.doxia.macro.MacroRequest;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributeSet;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eric
 */
@Component(role = Macro.class, hint = "plugininfo")
public class MojohausPluginsMacro extends AbstractMacro {

    private String getFinalUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.connect();
        conn.getInputStream();
        if (conn.getResponseCode() == 301 || conn.getResponseCode() == 302) {
            String redirectUrl = conn.getHeaderField("Location");
            return getFinalUrl(redirectUrl);
        }
        return url;
    }

    private String getContent(String urlasString, String type) {
        StringBuilder sb = new StringBuilder();
        try {

            HttpURLConnection conn = (HttpURLConnection) new URL(getFinalUrl(urlasString)).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", type);
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String output;
            getLog().info("Output from Server .... " + urlasString);
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            getLog().debug(sb.toString());
            conn.disconnect();
        } catch (MalformedURLException ex) {
            getLog().error(ex);
        } catch (IOException ex) {
            getLog().error(ex);
        }
        return sb.toString();
    }

    @Override
    public void execute(Sink sink, MacroRequest mr) throws MacroExecutionException {
        String s = (String) mr.getParameter("coordinate");
        String[] co = s.split(":");
        // get json object from search central api to get last version according to coordinate
        JSONObject jsobj = new JSONObject(getContent("http://search.maven.org/solrsearch/select?q=g:%22" + co[0] + "%22+AND+a:%22" + co[1] + "%22&rows=1&wt=json", "application/json"));
        JSONArray arr = jsobj.getJSONObject("response").getJSONArray("docs");
        // array but should be only one item
        for (int i = 0; i < arr.length(); i++) {
            try {
                
                String version = arr.getJSONObject(i).getString("latestVersion");
                /*
                * Having version we can build a file patern to get the pom (using search maven rest
                 */
                MavenXpp3Reader reader = new MavenXpp3Reader();
                String pomurl = "http://search.maven.org/remotecontent?filepath="
                        + co[0].replace('.', '/')
                        + "/"
                        + co[1].replace('.', '/')
                        + "/"
                        + version
                        + "/"
                        + co[1].replace('.', '/')
                        + "-"
                        + version
                        + ".pom";
                InputStream is = new ByteArrayInputStream(getContent(pomurl, "text/xml").getBytes());
                Model model = reader.read(is);
                sink.tableRow();
                boolean validURL = (model.getUrl() != null) && !model.getUrl().contains("codehaus.org");
                SinkEventAttributes attr = new SinkEventAttributeSet();
                if (model.getUrl() == null) {
                    attr.addAttribute(SinkEventAttributeSet.STYLE, "background-color:gray");
                } else if (!validURL) {
                    attr.addAttribute(SinkEventAttributeSet.STYLE, "background-color:orange");
                }
                sink.tableCell(attr);
                if (model.getUrl() != null) {
                    sink.link(model.getUrl());
                    sink.text(co[1].replaceAll("-maven-plugin", ""));
                    sink.link_();
                } else {
                    sink.text(co[1].replaceAll("-maven-plugin", ""));
                }
                sink.tableCell_();

                sink.tableCell();
                if (version != null) {
                    sink.text(version);
                } else {
                    sink.text(version);
                }
                //sink.text(aa.getUrl());

                sink.tableCell_();
                sink.tableCell();
                if (model.getDescription() != null) {
                    sink.rawText(model.getDescription());
                } else {
                    sink.rawText("(no description)");
                }
                sink.tableCell_();
                boolean validIssue = (model.getIssueManagement() != null) && !model.getIssueManagement().getUrl().contains("codehaus.org");
                SinkEventAttributes attr1 = new SinkEventAttributeSet();
                if (model.getIssueManagement() == null) {
                    attr1.addAttribute(SinkEventAttributeSet.STYLE, "background-color:gray");
                } else if (!validIssue) {
                    attr1.addAttribute(SinkEventAttributeSet.STYLE, "background-color:orange");
                }
                sink.tableCell(attr1);
                if (model.getIssueManagement() != null) {
                    sink.link(model.getIssueManagement().getUrl());
                    sink.text(model.getIssueManagement().getSystem().toUpperCase());
                    sink.link_();
                } else {
                    sink.text("-");
                }

                sink.tableCell_();
                sink.tableRow_();
            } catch (IOException | XmlPullParserException ex) {
                throw new MacroExecutionException("Error during macro application", ex);
            }
        }
    }

}
