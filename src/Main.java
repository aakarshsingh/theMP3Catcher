import java.io.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.Properties;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.ResponseException;
import com.jaunt.UserAgent;

/**
 * Reads a text file with this formatting <id> <Artist>~~~<Title>
 *
 * A snippet from the text file:
 *           1 Erato~~~Ambitions
 *           2 Jason Mraz~~~I'm Yours
 *           3 Jose Gonzales~~~Heartbeats
 *           4 The Head And The Heart~~~Rivers and Roads
 *
 * You can achieve this formatting easily using Spotify and Sublime Text and a few online tools.
 *
 * The path to the text file is passed as a command line argument
 *
 * The Main class iterates over the file and send every <Artist, Title> pair to the YouTubeSearch class
 *
 * YouTubeSearch class
 *
 * String queryTerm = ARTIST.concat(" - ").concat(TITLE);
 *
 * Uses this queryTerm and retrieves only the top result according to viewCount
 *
 * Performs a check whether the title of this retrieved video contains the Title we passed or not
 *
 * This youTube slug is now passed to the RetrieverClass which will rip it to MP3.
 *
 * Read readme/inspiration.txt to understand the thought flow behind the code
 *
 * Read readme/dependencies.txt to understand how to get this code working
 *
 * Further edits above the Retriever Class
 *
 */
public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException
    {
        List<String> lines = Files.readAllLines(Paths.get(args[0]), Charset.defaultCharset());
        int numberOfFiles = lines.size();
        for (int i = 0; i < numberOfFiles; i++)
        {
            String currentLine = lines.get(i);
            Pattern p = Pattern.compile("\\p{L}");
            Matcher m = p.matcher(currentLine);
            m.find();
            String artist = currentLine.substring(m.start(), currentLine.indexOf('~'));
            String song = currentLine.substring(currentLine.lastIndexOf('~') + 1);
            YouTubeSearch.setter(artist, song);
        }
    }
}

class YouTubeSearch
{
    private static final String PROPERTIES_FILENAME = "youtube.properties";
    private static final long NUMBER_OF_VIDEOS_RETURNED = 1;

    static void setter(String ARTIST,String TITLE) throws IOException, InterruptedException
    {
        Properties properties = new Properties();
        InputStream in = YouTubeSearch.class.getResourceAsStream("/" + PROPERTIES_FILENAME);
        properties.load(in);
        YouTube youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, new HttpRequestInitializer()
        {
            public void initialize(HttpRequest request) throws IOException
            {
            }
        }).setApplicationName("themp3catcher").build();

        String queryTerm = ARTIST.concat(" - ").concat(TITLE);
        YouTube.Search.List search = youtube.search().list("id,snippet");
        String apiKey = properties.getProperty("youtube.apikey");
        search.setKey(apiKey);
        search.setQ(queryTerm);
        search.setType("video");
        search.setFields("items(id/kind,id/videoId,snippet/title)");
        search.setMaxResults(NUMBER_OF_VIDEOS_RETURNED);
        search.setOrder("viewcount");

        SearchListResponse searchResponse = search.execute();
        List<SearchResult> searchResultList = searchResponse.getItems();
        Iterator<SearchResult> iteratorSearchResults=searchResultList.iterator();
        SearchResult singleVideo = iteratorSearchResults.next();
        ResourceId rId = singleVideo.getId();
        String youTubeTitle=singleVideo.getSnippet().getTitle();

        if(checkResourceKind(rId)&&checkTitle(youTubeTitle, TITLE))
        {
            new RetrieverClass(rId.getVideoId(),queryTerm);
        }
        else
        {
            System.out.println("Oops.. YouTube Search did not return any results for "+queryTerm);
        }
    }

    private static boolean checkTitle(String youTubeTitle, String title)
    {
        return youTubeTitle.contains(title);
    }

    private static boolean checkResourceKind(ResourceId rId)
    {
        return rId.getKind().equals("youtube#video");
    }
}

/**
 * Using http://jaunt-api.com/ for this class
 *
 * Added jaunt1.0.jar as an External Library
 *
 * Using youtubeinmp3.com for downloading the MP3 File
 *
 *
 */
class RetrieverClass
{
    private static String youTubeSlug;
    private final String fileName;
    private final File downloadFile;

    RetrieverClass(String x, String y) throws InterruptedException
    {
        this.youTubeSlug = x;
        this.fileName=y;
        this.downloadFile=new File("C:\\Users\\"+System.getProperty("user.name")+"\\Music\\"+fileName+".mp3");
        retrieveDownloadURL();
    }

    private void retrieveDownloadURL()
    {
        UserAgent userAgent = new UserAgent();
        try
        {
            String videoURL = "http://www.youtube.com/watch?v=" + youTubeSlug;
            String url = "http://youtubeinmp3.com/download/?video=http://youtubeinmp3.com/download/?video=" + videoURL;
            userAgent.visit(url);
            String downloadUrl="";
            com.jaunt.Elements anchor = userAgent.doc.findEach("<a>");
            int i = 0;
            for (Element e : anchor)
            {
                i++;
                if (i == 2)
                {
                    downloadUrl = e.getAt("href");
                    break;
                }
            }
            if(nowDownloadTheFile(downloadUrl))
            {
                printFinalMessage(0);
            }
            else
            {
                printFinalMessage(1);
            }
        }
        catch (JauntException e)
        {
            System.out.println(e);
        }

    }

    private boolean nowDownloadTheFile(String downloadUrl)
    {
        UserAgent downloader=new UserAgent();
        try
        {
            downloader.download(downloadUrl,downloadFile);
            return true;
        }
        catch (ResponseException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    private void printFinalMessage(int i)
    {
        switch (i)
        {
            case 0:
                System.out.println("Successfully downloaded "+fileName+" at "+downloadFile);
                break;
            case 1:
                System.out.println("There seem to be an error with your connection");
                break;
        }
    }
}
