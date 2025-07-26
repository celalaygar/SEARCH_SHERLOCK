package com.sherlock.search.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteResult {
    private String siteName;
    private String urlMain;
    private String urlUser;
    private QueryStatus status;
    private String httpStatus;



    @Override
    public String toString() {
        return "SiteResult{" +
                "siteName='" + siteName + '\'' +
                ", urlMain='" + urlMain + '\'' +
                ", urlUser='" + urlUser + '\'' +
                ", status=" + status +
                ", httpStatus='" + httpStatus + '\'' +
                '}';
    }
}
