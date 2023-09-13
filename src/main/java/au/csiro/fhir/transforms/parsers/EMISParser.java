/*
 * Copyright © 2018-2020, Commonwealth Scientific and Industrial Research Organisation (CSIRO)
 * ABN 41 687 119 230. Licensed under the CSIRO Open Source Software Licence Agreement.
*/

package au.csiro.fhir.transforms.parsers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r4.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.r4.model.CodeSystem.PropertyType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r4.model.ConceptMap.OtherElementComponent;
import org.hl7.fhir.r4.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r4.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import au.csiro.fhir.transforms.helper.FHIRClientR4;
import au.csiro.fhir.transforms.helper.FeedClient;
import au.csiro.fhir.transforms.helper.FeedUtility;
import au.csiro.fhir.transforms.helper.Utility;
import au.csiro.fhir.transforms.helper.atomio.Entry;
import ca.uhn.fhir.context.FhirContext;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;


class EMISMapEntry {
	String source;
	String sourceLabel;
	String target;
	String targetName;
	String equivalent;
	String comments;
}

public class EMISParser {

	public static void main(String[] args)
	{
		EMISParser parser = new EMISParser();
		try {
			FhirContext ctx = FhirContext.forR4();

			List<EMISMapEntry> entries = parser.loadSourceData("/Users/dougal/Code/Data/emis/EMIS_LOCAL_MAP_full_2.csv");
			ConceptMap cm = parser.produceConceptMap(entries, "https://prototype/", "0.0.1");

			File emisSCTMapFile = new File(cm.getId() + ".json");
			String visionMaxCMString = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(cm);
			Utility.toTextFile(visionMaxCMString, emisSCTMapFile);

		} catch (CsvValidationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public List<EMISMapEntry> loadSourceData(String mapFile)
			throws IOException, CsvValidationException {
		


		System.out.println("Process EMIS File " + mapFile);
		Map<String, EMISMapEntry> allMapEntries = new HashMap<String, EMISMapEntry>();

		FileReader filereader = new FileReader(mapFile, StandardCharsets.UTF_8);

		final CSVParser parser =
				new CSVParserBuilder()
				.withSeparator(',')
				.build();
		final CSVReader csvReader =
				new CSVReaderBuilder(filereader)
				.withSkipLines(1)
				.withCSVParser(parser)
				.build();

		String[] nextRecord;
		int progressCount = 0;
		// we are going to read data line by line
		while ((nextRecord = csvReader.readNext()) != null) {
			EMISMapEntry entry = new EMISMapEntry();
			entry.source = nextRecord[0];
			entry.sourceLabel = nextRecord[1];
			entry.target = nextRecord[2];
			entry.targetName = nextRecord[3];
			entry.equivalent = nextRecord[4];
			entry.comments = nextRecord[5];
			
			allMapEntries.put(entry.source, entry);
			progressCount++;
			if(progressCount % 1000 == 0)
			{
				System.out.println("Progress " + progressCount);
			}
		}


		System.out.printf("Total mapped codes - %s\n", allMapEntries.size());

		List<EMISMapEntry> asList = new ArrayList<EMISMapEntry>(allMapEntries.values());

		return asList;
	}

    private ConceptMap produceConceptMap(List<EMISMapEntry> mapRows, String baseUrl, String version)
    {
        String cmId = "emis-snomed-experimental-map";
		ConceptMap conceptMap = new ConceptMap();
		conceptMap.setId(cmId);
		conceptMap.setUrl(baseUrl + cmId)
                    .setDescription("A FHIR ConceptMap for emis local codes to SNOMED")
				    .setVersion(version).setTitle(cmId).setName("EMIS local code to SNOMED")
                    .setStatus(PublicationStatus.DRAFT)
				    .setExperimental(true)
                    .setSource(new UriType("http://prototype/emislocal/vs"))
                    .setTarget(new UriType("http://snomed.info/sct?fhir_vs=isa/138875005"))
                    .setPublisher("OL");
		ConceptMapGroupComponent groupComponent = new ConceptMapGroupComponent();
		conceptMap.addGroup(groupComponent);

		groupComponent.setSource("http://prototype/emislocal/vs");
		groupComponent.setSourceVersion(version);
		groupComponent.setTarget("http://snomed.info/sct");

        for(EMISMapEntry entry : mapRows) 
        {
            SourceElementComponent sourceElementComponent = new SourceElementComponent();
			sourceElementComponent.setCode(entry.source);
			sourceElementComponent.setDisplay(entry.sourceLabel);

            TargetElementComponent target = new TargetElementComponent();
            target.setCode(entry.target);
			target.setDisplay(entry.targetName);
            
			if(entry.equivalent.equals("equivalent"))
			{
            	target.setEquivalence(ConceptMapEquivalence.EQUIVALENT);
			}
			else
			{
				target.setEquivalence(ConceptMapEquivalence.RELATEDTO);
			}
			target.setComment(entry.comments);
            sourceElementComponent.addTarget(target);

			groupComponent.addElement(sourceElementComponent);
		}

		return conceptMap;

	}

}

