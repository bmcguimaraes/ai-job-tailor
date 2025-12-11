package com.bg.resume_analyser.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class JobDescriptionService {

    public String getJobDescriptionFromUrl(String url) throws IOException {
        System.out.println("[JobDescriptionService] Fetching and parsing URL: " + url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            // More specific selectors for LinkedIn
            Element titleElement = doc.selectFirst("h1.top-card-layout__title, .top-card-layout__title");
            Element companyElement = doc.selectFirst("a.topcard__org-name-link, span.topcard__flavor-label");
            Element descriptionElement = doc.selectFirst(".description__text");

            StringBuilder jobTextBuilder = new StringBuilder();

            if (titleElement != null) {
                jobTextBuilder.append("Job Title: ").append(titleElement.text()).append("\n");
            } else {
                 jobTextBuilder.append("Job Title: Not found\n");
            }

            if (companyElement != null) {
                jobTextBuilder.append("Company: ").append(companyElement.text()).append("\n\n");
            } else {
                jobTextBuilder.append("Company: Not found\n\n");
            }

            if (descriptionElement != null) {
                // Using wholeText() to preserve line breaks within the description
                jobTextBuilder.append(descriptionElement.wholeText());
            } else {
                // Fallback to the whole body text if the specific description element is not found
                System.err.println("[JobDescriptionService] Could not find specific description element, falling back to body text.");
                jobTextBuilder.append(doc.body().text());
            }
            
            String jobText = jobTextBuilder.toString();
            System.out.println("[JobDescriptionService] Successfully extracted job description text.");
            return jobText;

        } catch (IOException e) {
            System.err.println("[JobDescriptionService] FAILED to fetch URL: " + e.getMessage());
            throw new IOException("Could not fetch or parse the job description from the URL: " + url, e);
        }
    }
}

