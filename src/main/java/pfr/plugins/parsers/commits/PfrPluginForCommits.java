package pfr.plugins.parsers.commits;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import pfr.annotations.ConceptDeclaration;
import pfr.annotations.PropertyDeclaration;
import pfr.annotations.RelationDeclaration;

 

public class PfrPluginForCommits {
	@ConceptDeclaration public static final String COMMIT = "commit";
	@ConceptDeclaration public static final String PATCHES = "patches";
	@ConceptDeclaration public static final String PATCH = "patch";
	
	@PropertyDeclaration(parent=COMMIT)public static final String CONTENT = "content";
	@PropertyDeclaration(parent=COMMIT)public static final String SHA = "sha";
	@PropertyDeclaration(parent=COMMIT)public static final String COMMITURL = "url";
	@PropertyDeclaration(parent=COMMIT)public static final String MESSAGE = "message";
	@PropertyDeclaration(parent=COMMIT)public static final String AUTHOR = "author";
	@PropertyDeclaration(parent=COMMIT)public static final String AUTHORMAIL = "authorMail";
	@PropertyDeclaration(parent=COMMIT)public static final String AUTHORDATE = "authorDate";
	@PropertyDeclaration(parent=COMMIT)public static final String COMMITTER = "committer";
	@PropertyDeclaration(parent=COMMIT)public static final String COMMITTERMAIL = "committerMail";
	@PropertyDeclaration(parent=COMMIT)public static final String COMMITTERDATE = "committerDate";
	@PropertyDeclaration(parent=COMMIT)public static final String FILENAME = "fileName";
	@PropertyDeclaration(parent=COMMIT)public static final String COMMITPATCH = "commitPatch";
	@PropertyDeclaration(parent=COMMIT)public static final String STATUS = "status";


	@RelationDeclaration public static final String PARENT_TO="parentTo";
	@RelationDeclaration public static final String PATCHES_TO="patchesTo";
	@RelationDeclaration public static final String PATCH_TO="patchTo";
	
	public String path;
	public String path2;
	public Commits commits;
	
	//对外接口，设置存储commits的文件夹并解析
	public void setPath(String p, String p2) throws Exception {
		path = p;
		path2 = p2;
		commits = new Commits();
		commits.getCommits(parseFile(new File(p)));
		commits.getPatch(parsePatchFile(new File(p2)));
	}
	
	//爬取commits的爬虫
	public void getCommitsFromGit(String url, String outFileName) throws Exception {
		Document doc;
		int num = 0;
		File outFile = new File(outFileName);
		FileWriter writer = new FileWriter(outFile);
		while(true){
			num ++;
			doc = Jsoup.connect(url+"?page="+num).
			ignoreContentType(true).timeout(20000).get();
			Thread.sleep((long)(3000 + Math.random()*1000));
			
			String pre = doc.body().text();
			
			writer.write(pre+"\n");
			if(pre.length() < 10) break;
		}
		writer.close();
	}
	//爬取包含patch的commits详细信息
	public void getCommitsWithURLs(Commits commits, String outFileName) throws Exception {
		String OAuth = "?client_id=fa5191bf55e754d6d25b&client_secret=226fc9193e753ca8f69fd8d9279577e4a9c5448c";
		Document doc;
		File outFile = new File(outFileName);
		FileWriter writer = new FileWriter(outFile);
		for(Commit commit:commits.commits) {
			System.out.println(commit.url);
			//不能用get(),必须用execute()
			String pre = Jsoup.connect(commit.url + OAuth).maxBodySize(0)
					.ignoreContentType(true).timeout(10000).execute().body();
			//Thread.sleep((long)(1000 + Math.random()*1000));
			
			writer.write(pre+"\n");
		}
		writer.close();
	}
	
	//处理爬取获得commits格式，用于展示
	public void parseFile(File inFile, File outFile) throws Exception {
		FileWriter writer = new FileWriter(outFile);
		List<String> list = parseFile(inFile);
		for(String line:list) {
			writer.write(line+"\n");
		}
		writer.close();
	}
	//解析爬取commits文件，获得list
	public List<String> parseFile(File inFile) throws Exception {
		List<String> ans = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader (inFile));
		String line = reader.readLine();

		while (line != null) {
			int begin = 1;
			int end = 1;
			while (true) {
				end = line.indexOf("]}") + 2;
				if (end == 1) break;
				String commit = line.substring(begin, end);
				line = line.substring(end + 1);
				begin = 0;
				ans.add(commit);
			}
			line = reader.readLine();
		}
		reader.close();
		return ans;
	}
	//解析爬取patches文件，获得list
	public List<String> parsePatchFile(File inFile) throws Exception {
		List<String> ans = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader (inFile));
		String line = reader.readLine();

		while (line != null) {
			ans.add(line);
			line = reader.readLine();
		}
		reader.close();
		return ans;
	}
	//建立本地数据库，用于测试
	public void runTry(File dbFile) throws Exception{
		if(dbFile.exists()){
			dbFile.delete();
		}
		GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(dbFile);
		run(db);
	}
	
	//对外建图接口
	public void run(GraphDatabaseService db) {
		try (Transaction tx = db.beginTx()) {
			for(Commit commit :commits.commits) {
				commit.setNode(db.createNode());
				commit.node.addLabel(Label.label(COMMIT));
				commit.node.setProperty(CONTENT, commit.content);
				commit.node.setProperty(SHA, commit.sha);
				commit.node.setProperty(COMMITURL, commit.url);
				commit.node.setProperty(MESSAGE, commit.message);
				commit.node.setProperty(AUTHOR, commit.author.name);
				commit.node.setProperty(AUTHORMAIL, commit.author.email);
				commit.node.setProperty(AUTHORDATE, commit.author.date);
				commit.node.setProperty(COMMITTER, commit.committer.name);
				commit.node.setProperty(COMMITTERMAIL, commit.committer.email);
				commit.node.setProperty(COMMITTERDATE, commit.committer.date);
				Node patchesNode = db.createNode();
				patchesNode.addLabel(Label.label(PATCHES));
				patchesNode.createRelationshipTo(commit.node, RelationshipType.withName(PATCHES_TO));
				patchesNode.setProperty(MESSAGE, commit.patches.message);
				for(Patch patch:commit.patches.patches) {
					Node patchNode = db.createNode();
					patchNode.addLabel(Label.label(PATCH));
					patchNode.setProperty(SHA, patch.fileSha);
					patchNode.setProperty(FILENAME, patch.fileName);
					patchNode.setProperty(STATUS, patch.status);
					System.out.println(commit.sha);
					patchNode.setProperty(COMMITPATCH, patch.patch);
					patchNode.createRelationshipTo(patchesNode, RelationshipType.withName(PATCH_TO));
				}
			}
			Linker linker = new Linker(commits);
			linker.getParentLink();
			for(Entry<Commit, Commit> entry : linker.parentMap.entrySet()) {
				entry.getKey().node.createRelationshipTo(entry.getValue().node, RelationshipType.withName(PARENT_TO));
			}
			tx.success();
		}
		db.shutdown();
	}
	
	
	public static void main(String args[]) throws Exception {
		PfrPluginForCommits test = new PfrPluginForCommits();
		//获取commits
		//test.getCommitsFromGit("https://api.github.com/repos/apache/lucenenet/commits", "C:\\Users\\Liwp\\Desktop\\知识库\\test\\out.txt");


		//解析commits文件
		//test.parseFile(new File("C:\\Users\\Liwp\\Desktop\\知识库\\test\\out.txt"), new File("C:\\Users\\Liwp\\Desktop\\知识库\\test\\commits.txt"));
		//解析得到commits列表
		test.setPath("C:\\Users\\Liwp\\Desktop\\知识库\\test\\out.txt",
				"C:\\Users\\Liwp\\Desktop\\知识库\\test\\out2.txt");
		
		test.runTry(new File("C:\\Users\\Liwp\\Desktop\\知识库\\test\\try"));
		
		//爬取patch信息
//		Commits c = new Commits();
//		c.getCommits(test.parseFile(new File("C:\\Users\\Liwp\\Desktop\\知识库\\test\\out.txt")));
//		test.getCommitsWithURLs(c, "C:\\Users\\Liwp\\Desktop\\知识库\\test\\out2.txt");
		
	}
}

class Commits{
	public List<Commit> commits;
	//解析包含patch的commits
	public void getPatch(List<String> list) {
		int num = 0;
		for(Commit commit:commits) {
			commit.patches.getPatches(list.get(num));
			num++;
		}
	}
	
	//解析commits
	public void getCommits(List<String> list) {
		commits = new ArrayList<Commit>();
		for(String one : list) {
			Commit tmp = new Commit();
			tmp.content = one;
			int index = one.indexOf("\"parents\":[");
			if(index != -1) {
				one = one.substring(index);
				Pattern p = Pattern.compile("([a-z0-9]{40})");
				Matcher m = p.matcher(one);
				while(m.find()) {
					if(tmp.parents.contains(m.group(0))){
						continue;
					}
					tmp.parents.add(m.group(0));
				}
				commits.add(tmp);
			} else {
				tmp.parents = null;
				commits.add(tmp);
			}
		}
		getCommitsInfo();
	}
	//解析
	public void getCommitsInfo() {
		for(Commit commit:commits) {
			JSONObject json = new JSONObject(commit.content);
			commit.sha = json.getString("sha");
			commit.url = json.getString("url");
			JSONObject commitJ = json.getJSONObject("commit");
			commit.message = commitJ.getString("message");
			JSONObject authorJ = commitJ.getJSONObject("author");
			JSONObject committerJ = commitJ.getJSONObject("committer");
			commit.author.date = authorJ.getString("date");
			commit.author.email = authorJ.getString("email");
			commit.author.name = authorJ.getString("name");
			commit.committer.date = committerJ.getString("date");
			commit.committer.email = committerJ.getString("email");
			commit.committer.name = committerJ.getString("name");		
		}
	}
	
}

class Commit{
	public String sha;
	public String content;
	public String message;
	public String url;
	public List<String> parents;
	
	public Node node;
	public Patches patches;
	public Author author;
	public Committer committer;
	
	Commit() {
		sha = null;
		content = null;
		parents = new ArrayList<String>();
		node = null;
		url = null;
		patches = new Patches();
		author = new Author();
		committer = new Committer();
	}
	
	public void setNode(Node n) {
		this.node = n;
	}
}

class Author{
	public String name;
	public String email;
	public String date;
}

class Committer{
	public String name;
	public String email;
	public String date;
}

class Patches {
	public List<Patch> patches;
	public String message;
	//解析一个commit的patches
	public void getPatches(String s) {
		patches = new ArrayList<Patch>();
		JSONObject js = new JSONObject(s);
		message = js.getJSONObject("commit").getString("message");
		JSONArray array = js.getJSONArray("files");
		for(int i =0;i<array.length();i++) {
			System.out.println(js.get("url"));
			JSONObject json = array.getJSONObject(i);
			Patch patch = new Patch();
			if(json.isNull("sha")) {
				json.put("sha", "");
			}
			patch.fileSha = json.getString("sha");
			patch.fileName = json.getString("filename");
			patch.status = json.getString("status");
			if(json.has("patch")) {
				patch.patch = json.getString("patch");
			} else {
				patch.patch = "";
			}
			patches.add(patch);
		}
		
	}
}

class Patch {
	public String fileSha;
	public String fileName;
	public String patch;	
	public String status;
}

class Linker{
	public Commits commits;
	public Map<Commit,Commit> parentMap;
	public void getParentLink() {
		parentMap = new IdentityHashMap();
		for(Commit one:commits.commits) {
			if(one.parents == null) continue;
			if(one.parents.size() > 1 ){
				System.out.println(one.node.getId());
			}
			for(String parent:one.parents) {
				for(Commit two:commits.commits) {
					if(two.sha.equals(parent)) {
						Commit x = new Commit();
						x.content = one.content;
						x.sha = one.sha;
						x.node = one.node;
						x.parents = one.parents;
						parentMap.put(x, two);
						break;
					}
				}
			}
		}
	}
	Linker(Commits c) {
		commits = c;
	}
	
	
}

