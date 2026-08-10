-- Seeds the songs table on startup (see spring.sql.init.mode=always).
-- After the first successful run, set spring.sql.init.mode=never in
-- application.properties so it doesn't re-insert duplicates on restart.

DELETE FROM songs;

INSERT INTO songs (title, artist, duration, src, art, position) VALUES
('Mera Joota Hai Japani', 'Mohammed Rafi', 244, '/songs/01.mp3', NULL, 0),
('Pyar Hua Ikrar Hua', 'Manna Dey, Lata Mangeshkar', 298, '/songs/02.mp3', NULL, 1),
('Mera Naam Chin Chin Choo', 'Geeta Dutt', 210, '/songs/03.mp3', NULL, 2),
('Aa Ab Laut Chalen', 'Lata Mangeshkar', 267, '/songs/04.mp3', NULL, 3),
('Chaudhvin Ka Chand Ho', 'Mohammed Rafi', 253, '/songs/05.mp3', NULL, 4),
('Lag Ja Gale', 'Lata Mangeshkar', 231, '/songs/06.mp3', NULL, 5),
('Kya Se Kya Ho Gaya', 'Kishore Kumar', 275, '/songs/07.mp3', NULL, 6),
('Roop Tera Mastana', 'Kishore Kumar', 219, '/songs/08.mp3', NULL, 7),
('Yeh Dosti', 'Kishore Kumar, Manna Dey', 312, '/songs/09.mp3', NULL, 8),
('Mere Sapno Ki Rani', 'Kishore Kumar', 258, '/songs/10.mp3', NULL, 9),
('Ek Ladki Bheegi Bhaagi Si', 'Kishore Kumar', 241, '/songs/11.mp3', NULL, 10),
('Pal Pal Dil Ke Paas', 'Kishore Kumar', 227, '/songs/12.mp3', NULL, 11),
('Chura Liya Hai Tumne', 'Mohammed Rafi, Asha Bhosle', 289, '/songs/13.mp3', NULL, 12),
('Bindiya Chamkegi', 'Mohammed Rafi, Lata Mangeshkar', 246, '/songs/14.mp3', NULL, 13),
('Aap Jaisa Koi', 'Nazia Hassan', 234, '/songs/15.mp3', NULL, 14),
('Dum Maro Dum', 'Asha Bhosle', 264, '/songs/16.mp3', NULL, 15),
('O Mere Dil Ke Chain', 'Kishore Kumar', 251, '/songs/17.mp3', NULL, 16),
('Chingari Koi Bhadke', 'Kishore Kumar', 296, '/songs/18.mp3', NULL, 17),
('Mehbooba Mehbooba', 'R. D. Burman', 305, '/songs/19.mp3', NULL, 18),
('Yeh Shaam Mastani', 'Kishore Kumar', 238, '/songs/20.mp3', NULL, 19),
('Kabhi Kabhie Mere Dil Mein', 'Mukesh, Lata Mangeshkar', 292, '/songs/21.mp3', NULL, 20),
('Tum Bin Jaoon Kahan', 'Kishore Kumar', 249, '/songs/22.mp3', NULL, 21),
('Ajeeb Dastan Hai Yeh', 'Lata Mangeshkar', 225, '/songs/23.mp3', NULL, 22),
('Mera Dil Bhi Kitna Pagal Hai', 'Kishore Kumar', 262, '/songs/24.mp3', NULL, 23),
('Mujhse Mohabbat Ka Izhaar Karta', 'Satrang Music Official', 304, '/songs/25.mp3', NULL, 24);
