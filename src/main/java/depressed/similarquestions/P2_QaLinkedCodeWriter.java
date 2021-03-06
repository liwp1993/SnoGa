package depressed.similarquestions;

import java.util.Set;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import depressed.similarquestions.utils.SimilarQuestionTaskConfig;
import graphmodel.ManageElements;

public class P2_QaLinkedCodeWriter {

	SimilarQuestionTaskConfig config = null;
	
	public static void main(String[] args){
		P2_QaLinkedCodeWriter p=new P2_QaLinkedCodeWriter("apache-poi");
		p.run();
	}

	public P2_QaLinkedCodeWriter(String projectName) {
		config = new SimilarQuestionTaskConfig(projectName);
	}

	public void run(){
		GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(new File(config.graphPath));
		try (Transaction tx = db.beginTx()){
			ResourceIterator<Node> nodes=db.getAllNodes().iterator();
			while (nodes.hasNext()){
				Node node=nodes.next();
				if (node.hasProperty(SimilarQuestionTaskConfig.CODES_LINE))
					node.removeProperty(SimilarQuestionTaskConfig.CODES_LINE);
			}
			nodes=db.getAllNodes().iterator();
			while (nodes.hasNext()){
				Node node=nodes.next();
				if (!node.hasLabel(ManageElements.Labels.QUESTION)&&!node.hasLabel(ManageElements.Labels.ANSWER))
					continue;
				Set<Long> codeSet=new HashSet<Long>();
				Iterator<Relationship> rels=node.getRelationships(ManageElements.RelTypes.DOC_LEVEL_REFER,Direction.OUTGOING).iterator();
				while (rels.hasNext())
					codeSet.add(rels.next().getEndNode().getId());
				rels=node.getRelationships(ManageElements.RelTypes.LEX_LEVEL_REFER,Direction.OUTGOING).iterator();
				while (rels.hasNext())
					codeSet.add(rels.next().getEndNode().getId());
				String codeLine="";
				for (Long id:codeSet)
					codeLine+=id+" ";
				codeLine=codeLine.trim();
				node.setProperty(SimilarQuestionTaskConfig.CODES_LINE, codeLine);
			}
			tx.success();
		}
		db.shutdown();
	}

}
