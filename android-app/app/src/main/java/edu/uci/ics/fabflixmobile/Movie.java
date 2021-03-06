package edu.uci.ics.fabflixmobile;

public class Movie {

    private final String id;

    private final String title;

    private final int year;

    private final String director;

    private final String genres;

    private final String stars;

    public Movie(String id, String title, int year, String director, String genres, String stars) {
        this.id = id;
        this.title = title;
        this.year = year;
        this.director = director;
        this.genres = genres;
        this.stars = stars;

    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

    public String getDirector() {
        return director;
    }

    public String getGenres() {
        return genres;
    }

    public String getStars() {  return stars;  }

    public String toString() {

        return "ID:" + getId() + ", " +
                "Title:" + getTitle() + ", " +
                "Year:" + getYear() + ", " +
                "Director:" + getDirector() + "." +
                "Genres:" + getGenres() + ".";
    }
}