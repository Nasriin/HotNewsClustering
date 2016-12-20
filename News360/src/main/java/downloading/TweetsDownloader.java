package git.Nasrin.News360.downloadTweets;
/**
 * author:	Nasrin Baratalipour
 * date:	2016/12/12
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TweetsDownloader {
	final  int TWEETS_WANTED_COUNT = 5000;
	final  int MAX_COUNT = 100;
	
	public static void main(String[] args) throws TwitterException, IOException {
		
		TweetsDownloader tDownloader = new TweetsDownloader();

		String time = "2016-12-17";
		String parentPath = "tweets";
		String seedPath = "seed.txt";
		
		System.out.println("start configuration..");
		twitter4j.Twitter twitter = tDownloader.configuration();
		System.out.println("finish configuration..");
		
		System.out.println("start reading seed file..");
		ArrayList<String> newsLinks = tDownloader.ReadSeed(seedPath);
		System.out.println("finish reading seed file..\n Number of seeds: " + newsLinks.size());
		
		System.out.println("start downloading the tweets..");
		tDownloader.saveTweetsToFiles(time, parentPath, twitter, newsLinks);
		System.out.println("All the tweets tweeted from " + time + "till now save in " + parentPath);
		
		System.out.println("TweetsDownloader.main()");
	}

	private void saveTweetsToFiles(String time, String parentPath,
			twitter4j.Twitter twitter, ArrayList<String> newsLinks)
			throws IOException {
		for(String newsLink : newsLinks){
			String queryStr = createQuery(time, newsLink);
			
			String newsAgency = newsLink.split("https://twitter.com/")[1];
			File newsAgencyFolder = new File(parentPath,newsAgency);
			exportTweets(twitter, newsAgencyFolder, queryStr);
		}
	}

	private ArrayList<String> ReadSeed(String seedPath)
			throws FileNotFoundException, IOException {
		ArrayList<String> newsLinks = new ArrayList<String>();
		BufferedReader bf = new BufferedReader(new InputStreamReader(new FileInputStream(new File(seedPath))));		
		String news = bf.readLine();
		while(news != null){
			try{
				String link = bf.readLine();
				newsLinks.add(link);
			} catch(NullPointerException e) {
				System.err.println("There is no link for " + news);
			};
			news = bf.readLine();
		}
		bf.close();
		return newsLinks;
	}

	private twitter4j.Twitter configuration() {
		ConfigurationBuilder cf = new ConfigurationBuilder();
		cf.setDebugEnabled(true)
		.setOAuthConsumerKey("rwaNZ5BFHrF9hBeBvp1Frd5rL")
		.setOAuthConsumerSecret("StNBUV2BVZEApuNWoPBXg5HNO640dsUZ27gU99rpn6oUNkKlyN")
		.setOAuthAccessToken("809155344492621824-AoQ3YKKT77eyKGHHg4vPO4CxwlPjUCF")
		.setOAuthAccessTokenSecret("55AJ4Os77M2JxluEVEGjjL09SMMfmBU5IMA2XtELUuD7E");

		TwitterFactory tf = new TwitterFactory(cf.build());
		twitter4j.Twitter twitter = tf.getInstance();
		return twitter;
	}

	private  void exportTweets(twitter4j.Twitter twitter, File newsAgencyFolder, String queryStr) throws IOException {
		long lastID = Long.MAX_VALUE;				  
		Query query = new Query(queryStr);
		
		if (newsAgencyFolder.exists()){
			File[] listofFiles = newsAgencyFolder.listFiles();
			for(File file: listofFiles) {
				long id = Long.parseLong(file.getName().split(".ser")[0]);
				if (id < lastID)
					lastID = id;
			}
			query.setMaxId(lastID-1);
		} else {
			System.out.println(newsAgencyFolder +" doesn't exists!");
			newsAgencyFolder.mkdir();
		}
		
		ArrayList<Status> tweets = new ArrayList<Status>();
		boolean nextPage = true;
		while (tweets.size () < TWEETS_WANTED_COUNT && nextPage) {
			if (TWEETS_WANTED_COUNT - tweets.size() > MAX_COUNT)
				query.setCount(MAX_COUNT);
			else 
				query.setCount(TWEETS_WANTED_COUNT - tweets.size());
			try {
				QueryResult result = twitter.search(query);
				if(result.getTweets().size() < MAX_COUNT)
					nextPage = false;
				tweets.addAll(result.getTweets());
//					System.out.println("Gathered " + tweets.size() + " tweets");
				for (Status t: result.getTweets()) 
					if(t.getId() < lastID) lastID = t.getId();
			}
			catch (TwitterException te) {
				System.out.println("Couldn't connect: " + te);
			}; 
			query.setMaxId(lastID-1);
		}
		System.out.println("Number of tweets: " + tweets.size());
		System.out.println("----------------------------------");
		
		for (Status tweet : tweets) {
			
//			pw.println(status.getText().replace("\n", "\t"));
			File tweetFile = new File(newsAgencyFolder, tweet.getId() + ".ser");
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tweetFile));
			out.writeObject(tweet);
			out.close();
		}
		System.out.printf("%d tweets saved into %s\n", tweets.size(), newsAgencyFolder);
	}

	private String createQuery(String time, String newsLink) {
		String newsTwitterId = newsLink.split("https://twitter.com/")[1];
		String queryStr = new String("from:" +newsTwitterId+" since:"+time);
		System.out.println("Query: \""+queryStr+"\"");
		return queryStr;
	}
}
