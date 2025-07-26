# Sherlock Java

Sherlock Java is a Java-based version of the popular [Sherlock Project](https://github.com/sherlock-project/sherlock), which allows searching for usernames across many social networks.

## Features

- Search for a given username across over 300 social media platforms
- Java-based implementation for backend integration
- Configurable and extensible
- Easy to deploy in microservice environments

## Technologies Used

- Java 17+
- Spring Boot
- Maven or Gradle
- REST APIs
- Nginx for reverse proxy

## Installation

### Requirements

- Java 17 or newer
- Maven or Gradle
- Nginx (optional for reverse proxy)

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/sherlock-java.git
   ```

2. Navigate to the project directory:
   ```bash
   cd sherlock-java
   ```

3. Build the project using Maven:
   ```bash
   mvn clean install
   ```

4. Run the application:
   ```bash
   java -jar target/sherlock-java.jar
   ```

## Configuration

Edit the `application.yml` or `application.properties` file to configure:

- Server port
- Timeout settings
- API endpoints

## Usage

You can send a request to the `/search` endpoint with the username you want to query:
```http
curl --location 'http://localhost:8080/api/search/name/celalaygar'
```

## Example Response

```json
[
    {
        "siteName": "Academia.edu",
        "urlMain": "https://www.academia.edu/",
        "urlUser": "https://independent.academia.edu/celalaygar",
        "status": "CLAIMED",
        "httpStatus": "200"
    },
    {
        "siteName": "AdmireMe.Vip",
        "urlMain": "https://admireme.vip/",
        "urlUser": "https://admireme.vip/celalaygar",
        "status": "CLAIMED",
        "httpStatus": "301"
    },
    {
        "siteName": "threads",
        "urlMain": "https://www.threads.net/",
        "urlUser": "https://www.threads.net/@celalaygar",
        "status": "CLAIMED",
        "httpStatus": "302"
    }
]
```

## Reverse Proxy with Nginx

Example configuration for Nginx:

```nginx
location /search_sherlock/ {
    proxy_pass http://127.0.0.1:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 600s;
    proxy_connect_timeout 600s;
    send_timeout 600s;
}
```

## License and Attribution

This project is based on the original [Sherlock Project](https://github.com/sherlock-project/sherlock) by Siddharth Dushantha.  
It is licensed under the MIT License. The original license and author attribution are preserved.
