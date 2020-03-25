package com.journear.app.core.entities;

public class NearbyDevices {
        private int id;
        private String source;
        private String destination;
        private String travelTime;
        private String user_rating;
        User user1 = new User();
        public NearbyDevices(){

        }
        public NearbyDevices(String source,String destination, String travelTime, String user_rating){
            this.source = source;
            this.destination = destination;
            this.travelTime = travelTime;
            this.user_rating = user_rating;
        }



        public NearbyDevices(int id, String source, String destination, String travelTime, String user_rating){
            this.id = id;
            this.source = source;
            this.destination = destination;
            this.travelTime = travelTime;
            this.user_rating = user_rating;

        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getTravelTime() {
            return travelTime;
        }

        public void setTravelTime(String travelTime) {
            this.travelTime = travelTime;
        }

        public String getUser_rating() {
            return user_rating;
        }

        public void setUser_rating(String user_rating) {
            this.user_rating = user_rating;
        }

    }