/*
References:
http://tutorials.jenkov.com/javafx/webview.html
https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebEngine.html
https://docs.oracle.com/javase/8/javafx/api/javafx/scene/layout/GridPane.html
https://github.com/rometools/rome
https://docs.oracle.com/javafx/2/text/jfxpub-text.htm
*/

//DEPS org.openjfx:javafx-controls:15.0.1:${os.detected.jfxname}
//DEPS org.openjfx:javafx-graphics:15.0.1:${os.detected.jfxname}
//DEPS org.openjfx:javafx-web:15.0.1:${os.detected.jfxname}
//DEPS com.rometools:rome:1.15.0

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.scene.web.WebHistory.Entry;
import javafx.stage.Stage;

public class RSSReader extends Application {
	
	WebView webView = new WebView();
	List<Post> posts = new ArrayList<>();
	GridPane menu = new GridPane();
	TextField feedInput = new TextField();
	FlowPane postHeader = new FlowPane();
	Label postHeaderLabel = new Label("Site - Title - Date");
	Post currentPost;
	boolean isShowingFeedPost;
	
	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("JavaFX WebView Example");
		
		try (Scanner sc = new Scanner(new File(".myfeed"))) {
			while (sc.hasNextLine()) posts.addAll(fetchFeed(sc.nextLine()));
			loadFeed();
		} catch (Exception e) { e.printStackTrace(); }

		ScrollPane scrollMenu = new ScrollPane();
		scrollMenu.setContent(menu);

		postHeaderLabel.setPadding(new Insets(10, 10, 10, 10));
		postHeaderLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

		postHeader.minHeight(100);
		postHeader.getChildren().add(postHeaderLabel);
		VBox postView = new VBox(postHeader, webView);
		
		SplitPane splitPane = new SplitPane();
		splitPane.getItems().addAll(scrollMenu, postView);
		
		VBox vbox = new VBox(getAddFeedPane(), splitPane);
		
        Scene scene = new Scene(vbox, 1200, 600);

        primaryStage.setScene(scene);
        primaryStage.show();
	}
	
	private FlowPane getAddFeedPane() {
		Label addLabel = new Label("Add feed:");
		feedInput.setPrefColumnCount(40);
		feedInput.setOnKeyPressed(ke -> handleNewFeed(ke));
		Button addButton = new Button("ADD");
		addButton.setOnAction(action -> handleNewFeed(null));
		Button previousPage = new Button(" Previous Page < ");
		previousPage.setOnAction(a -> handlePreviousPage());
		Button nextPage = new Button(" > Next Page");
		nextPage.setOnAction(a -> handleNextPage());
		return new FlowPane(addLabel, feedInput, addButton, previousPage, nextPage);
	}

	private void handlePreviousPage() {
		WebHistory webHistory = webView.getEngine().getHistory();
		assert debugHistory(webHistory);
		if (webHistory.getEntries().size() == 0) return;
		if (webHistory.getCurrentIndex() == 0) {
			showPost();
		} else {
			webHistory.go(-1);
		}
	}

	private void handleNextPage() {
		WebHistory webHistory = webView.getEngine().getHistory();
		assert debugHistory(webHistory);
		ObservableList<Entry> entries = webHistory.getEntries();
		if (webHistory.getCurrentIndex() + 1 >= entries.size()) return;
		if (isShowingFeedPost) {
			isShowingFeedPost = false;
			webView.getEngine().load(entries.get(0).getUrl());
		} else {
			webHistory.go(1);
		}
	}

	private boolean debugHistory(WebHistory history) {
		System.out.println("history.getCurrentIndex() = " + history.getCurrentIndex());
		System.out.println("history.getEntries().size() = " + history.getEntries().size());
		ObservableList<Entry> entries = history.getEntries();
		for (var e : entries) {
			System.out.println(e);
		}
		return true;
	}

	private void handleNewFeed(KeyEvent ke) {
		if (ke == null || ke.getCode().equals(KeyCode.ENTER)) {
			addFeedToFile(feedInput.getText());
			loadFeed(feedInput.getText());
			feedInput.setText("");
		}
	}
	
	private void addFeedToFile(String url) {
		try (FileWriter fw = new FileWriter(".myfeed", true)) {
			fw.write(url + "\n");
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private void loadFeed() {
		menu.getChildren().clear();
		posts.sort(Comparator.comparing(Post::getDate).reversed());
		for (var post : posts) menu.addColumn(0, post.linkToContent);
	}
	private void loadFeed(String url) {
		posts.addAll(fetchFeed(url));
		loadFeed();
	}
	
	private static class Post {
		String siteTitle;
		String postTitle;
		Date date;
		String content;
		Hyperlink linkToContent;
		Date getDate() { return date; }
	}
	
	public List<Post> fetchFeed(String url) {
		List<Post> listPost = new ArrayList<>();
        try {
            URL feedUrl = new URL(url);
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(feedUrl));
			
            // Get the entry items...
            for (SyndEntry entry : (List<SyndEntry>) feed.getEntries()) {
				if (entry.getContents().size() == 0) break;
				Post post = new Post();
				post.siteTitle = feed.getTitle();
				post.postTitle = entry.getTitle();
				post.content = entry.getContents().get(0).getValue();
				Date date = entry.getUpdatedDate();
				if (date == null) date = entry.getPublishedDate();
				if (date == null) date = new Date();
				post.date = date;
				Hyperlink link = new Hyperlink(entry.getTitle() + " - " + date);
				link.setOnAction(e -> showPost(post));
				post.linkToContent = link;
				listPost.add(post);
            }
        } catch (Exception ex) {
			System.err.println("Failed to fetch rss from: " + url);
			ex.printStackTrace();
        }
		
		return listPost;
	}

	private void showPost() {
		if (currentPost == null) return;
		isShowingFeedPost = true;
		updatePostHeader(currentPost);
		updateWebView(currentPost.content);
	}
	
	private void showPost(Post post) {
		currentPost = post;
		showPost();
	}
	
	private void updatePostHeader(Post post) {
		postHeaderLabel.setText(post.siteTitle + " - " + post.postTitle + " - " + post.date);
	}

	private void updateWebView(String content) {
		webView.getEngine().loadContent(content);
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
