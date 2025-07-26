package com.sherlock.search.controller;


import com.sherlock.search.model.SiteResult;
import com.sherlock.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchEngineController {

    public final SearchService searchService;


    @GetMapping("/name/{name}")
    public List<SiteResult> searchByName(@PathVariable String name){

        try {
            return searchService.searchByName(name);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
