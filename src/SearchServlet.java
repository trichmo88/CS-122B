import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * A servlet that takes input from a html <form> and talks to MySQL moviedbexample,
 * generates output as a html <table>
 */

// Declaring a WebServlet called FormServlet, which maps to url "/form"
@WebServlet(name = "SearchServlet", urlPatterns = "/api/search")
public class SearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private String currentNMovies;
    private String currentPageNumber;
    private String currentSortingOption;
    // holds the next page of results
    private JsonArray nextPageResults;

    // Create a dataSource which registered in web.xml
    private DataSource dataSource;

    public void init(ServletConfig config) {
        // initialize current page number to 0 for first call of class
        currentNMovies = "";
        currentPageNumber = "";
        currentSortingOption = "";
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedbexample");
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    private JsonArray generateResults(PrintWriter out, HttpServletResponse response, Connection dbCon,
                                      String title, String year, String director, String star,
                                      String nMovies, String pageNumber, String sortingOption)
    throws IOException {
        boolean[] parameters = {false, false, false, false};
        // Generate a SQL query
        String query = "SELECT distinct movies.id, title, year, director, rating from ratings, movies";
        if (!star.isEmpty())
        {
            query += ", stars_in_movies, stars where stars.name like ? and stars_in_movies.movieId = movies.id and stars.id = stars_in_movies.starId";
            parameters[0] = true;
        }
        if (!title.isEmpty())
        {
            if (star.isEmpty())
            {
                query += " where ";
            }
            else
            {
                query += " and ";
            }
            query+= "title like ?";
            parameters[1] = true;
        }
        if (!year.isEmpty())
        {
            if(title.isEmpty() && star.isEmpty())
            {
                query += " where ";
            }
            else
            {
                query += " and ";
            }
            query += "year=?";
            parameters[2] = true;
        }
        if (!director.isEmpty())
        {
            if(title.isEmpty() && year.isEmpty() && star.isEmpty())
            {
                query += " where ";
            }
            else
            {
                query += " and ";
            }
            query += "director like ?";
            parameters[3] = true;
        }
        query += " and ratings.movieId = movies.id\n";
        // sorting option if wanted - if none of these selected, return rows as is from database
        switch (sortingOption) {
            case "titleRatingASCE": query += "order by title, rating\n";        break;
            case "titleRatingDESC": query += "order by title desc, rating\n";   break;
            case "ratingTitleASCE": query += "order by rating, title\n";        break;
            case "ratingTitleDESC": query += "order by rating desc, title\n";   break;
        }
        query += "limit ?\n" +
                "offset ?";

        // get top 3 stars by most movies starred in
        String query2 = "select s.id, s.name, count(movieId) as movies\n" +
                "from (\n" +
                "select stars.id, name\n" +
                "from stars_in_movies, stars\n" +
                "where movieId=? and stars.id = stars_in_movies.starId\n" +
                "order by id\n" +
                ") as s, stars_in_movies\n" +
                "where s.id = stars_in_movies.starId\n" +
                "group by s.id\n" +
                "order by movies desc\n" +
                "limit 3";

        // get top 3 genres
        String query3 = "select genres.id, name\n" +
                "from genres_in_movies, genres\n" +
                "where movieId=? and genres.id = genres_in_movies.genreId\n" +
                "order by name\n" +
                "limit 3";

        try (PreparedStatement statement = dbCon.prepareStatement(query);
             PreparedStatement statement2 = dbCon.prepareStatement(query2);
             PreparedStatement statement3 = dbCon.prepareStatement(query3)) {
            int counter = 1;
            for (int i = 0; i < parameters.length; i++) {
                switch (i) {
                    case 0:
                        if (parameters[i]) { statement.setString(counter, '%'+star+'%');     counter++; break; }
                    case 1:
                        if (parameters[i]) { statement.setString(counter, '%'+title+'%');    counter++; break;}
                    case 2:
                        if (parameters[i]) { statement.setInt(counter, Integer.parseInt(year)); counter++; break;}
                    case 3:
                        if (parameters[i]) { statement.setString(counter, '%'+director+'%'); counter++; break;}
                }
            }
            statement.setInt(counter, Integer.parseInt(nMovies));
            statement.setInt(counter+1, Integer.parseInt(pageNumber) * Integer.parseInt(nMovies));

            ResultSet rs = statement.executeQuery();

            JsonArray jsonArray = new JsonArray();

            // Iterate through each row of rs and create a table row <tr>
            while (rs.next()) {
                String movie_id = rs.getString("id");
                String movie_title = rs.getString("title");
                String movie_year = rs.getString("year");
                String movie_director = rs.getString("director");
                String movie_rating = rs.getString("rating");


                // Create a JsonObject based on the data we retrieve from rs
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("movie_id", movie_id);
                jsonObject.addProperty("movie_title", movie_title);
                jsonObject.addProperty("movie_year", movie_year);
                jsonObject.addProperty("movie_director", movie_director);
                jsonObject.addProperty("movie_rating", movie_rating);

                jsonArray.add(jsonObject);
            }
            for (int i = 0; i<jsonArray.size(); i++)
            {
                JsonObject movie = jsonArray.get(i).getAsJsonObject(); //convert from jsonElement to jsonObject
                String movie_id = String.valueOf(movie.get("movie_id"));
                statement2.setString(1, movie_id.substring(1, movie_id.length()-1));

                ResultSet rstemp = statement2.executeQuery(); //Another resultset to execute 2nd query
                String stars = "";
                String starIds = "";
                while (rstemp.next())
                {
                    stars += rstemp.getString("name") + ", "; //append each star_id to empty string
                    starIds += rstemp.getString("id") + ", ";
                }
                if (!starIds.isEmpty()) {
                    starIds = starIds.substring(0, starIds.length()-2);
                    stars = stars.substring(0, stars.length()-2);
                }
                movie.addProperty("star_ids", starIds);
                movie.addProperty("star_names", stars); //add the star_ids as a property of the jsonObject
                rstemp.close();
            }

            // For loop that iterates through the already created jsonArray and modifies each
            // JsonObject to include the three genres
            for (int i = 0; i<jsonArray.size(); i++)
            {
                JsonObject movie = jsonArray.get(i).getAsJsonObject();
                String movie_id = String.valueOf(movie.get("movie_id"));
                statement3.setString(1, movie_id.substring(1, movie_id.length()-1));

                ResultSet rstemp = statement3.executeQuery();
                String genres = "";
                String genreIDs = "";
                while (rstemp.next())
                {
                    genres += rstemp.getString("name") + ", ";
                    genreIDs += rstemp.getString("id") + ", ";
                }
                if (!genreIDs.isEmpty()) {
                    genreIDs = genreIDs.substring(0, genreIDs.length()-2);
                    genres = genres.substring(0, genres.length()-2);
                }
                movie.addProperty("genre_names", genres);
                movie.addProperty("genre_ids", genreIDs);
                rstemp.close();
            }

            rs.close();
            statement.close();
            statement2.close();
            statement3.close();
            return jsonArray;
        } catch (Exception e) {
            // write error message JSON object to output
            JsonObject jsonObject = new JsonObject();
            JsonArray errorArray = new JsonArray();
            jsonObject.addProperty("errorMessage", e.getMessage() + "from generateResults");
            errorArray.add(jsonObject);

            // set response status to 500 (Internal Server Error)
            response.setStatus(500);
            return errorArray;
        }
    }

    // Use http GET
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json; charset=utf-8");    // Response mime type

        PrintWriter out = response.getWriter();
        String title = request.getParameter("title");
        String year = request.getParameter("year");
        String director = request.getParameter("director");
        String star = request.getParameter("star");
        String nMovies = request.getParameter("nMovies");
        String pageNumber = request.getParameter("page");
        String sortingOption = request.getParameter("sorting");

        try (Connection dbCon = dataSource.getConnection()) {
            // Create a new connection to database

            // if the page is an even number, always generate the current page of results and the next
            if (Integer.parseInt(pageNumber) % 2 == 0) {
                JsonArray jsonArray = generateResults(out, response, dbCon,
                                                      title, year, director, star,
                                                      nMovies, pageNumber, sortingOption);
                out.write(jsonArray.toString());
                JsonArray nextPageArray = generateResults(out, response, dbCon,
                        title, year, director, star,
                        nMovies, Integer.toString(Integer.parseInt(pageNumber)+1), sortingOption);
                currentNMovies = nMovies;
                currentPageNumber = pageNumber;
                currentSortingOption = sortingOption;
                nextPageResults = nextPageArray;
            }
            // if the page number is an odd number
            else if (Integer.parseInt(pageNumber) % 2 == 1) {
                // if the page number is the next page,
                if (Integer.parseInt(pageNumber) == Integer.parseInt(currentPageNumber)+1) {
                    // if the settings are not the same, generate new nextPageResults and send
                    if (!currentNMovies.equals(nMovies) || !currentSortingOption.equals(sortingOption)) {
                        nextPageResults = generateResults(out, response, dbCon,
                                                          title, year, director, star,
                                                          nMovies, pageNumber, sortingOption);
                        currentNMovies = nMovies;
                        currentSortingOption = sortingOption;
                    }
                    // statement reached if settings are same thus send nextPage
                    currentPageNumber = pageNumber;
                    out.write(nextPageResults.toString());
                }
                // if the page number is not the next page, generate results for previous page and send
                else {
                    JsonArray previousPageResults = generateResults(out, response, dbCon,
                                                                    title, year, director, star,
                                                                    nMovies, pageNumber, sortingOption);
                    currentNMovies = nMovies;
                    currentSortingOption = sortingOption;
                    currentPageNumber = pageNumber;
                    out.write(previousPageResults.toString());
                }
            }
            response.setStatus(200);
        } catch (Exception e) {
            // write error message JSON object to output
            JsonObject jsonObject = new JsonObject();
            JsonArray errorArray = new JsonArray();
            jsonObject.addProperty("errorMessage", e.getMessage());
            errorArray.add(jsonObject);
            out.write(jsonObject.toString());

            // set response status to 500 (Internal Server Error)
            response.setStatus(500);
        } finally {
            out.close();
        }
        // always remember to close db connection after usage. Here it's done by try-with-resources
    }
}
