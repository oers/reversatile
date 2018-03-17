package de.earthlingz.oerszebra;

import com.shurik.droidzebra.ZebraEngine;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;

/**
 * Created by stefan on 17.03.2018.
 */
public class DroidZebraJunitTest {

    @Test
    public void playOk() {
        LinkedList<ZebraEngine.Move> moves = DroidZebra.makeMoveList("[Event \"?\"]\n" +
                "[Site \"PlayOK\"]\n" +
                "[Date \"2017.12.28\"]\n" +
                "[Round \"-\"]\n" +
                "[Black \"xmav000\"]\n" +
                "[White \"strongheart\"]\n" +
                "[Result \"62-1\"]\n" +
                "[Time \"01:10:45\"]\n" +
                "[TimeControl \"180\"]\n" +
                "[BlackElo \"1829\"]\n" +
                "[WhiteElo \"1316\"]\n" +
                "\n" +
                "1. d3 c5 2. d6 e3 3. f4 e6 4. b4 c4 5. c3 d2 6. e2 c2 7. f3 b5 8. f5 a3 9. b6\n" +
                "c6 10. c1 g5 11. a4 b3 12. a5 a6 13. g6 g4 14. h6 h5 15. h4 f1 16. d1 g3 17. f2\n" +
                "g1 18. f6 f7 19. b1 h3 20. h2 g2 21. f8 g7 22. d7 e7 23. c7 b2 24. h1 d8 25. e1\n" +
                "b7 26. h8 h7 27. g8 -- 28. a8 a7 29. a2 -- 30. b8 -- 31. c8 -- 32. e8 62-1");

        Assert.assertThat(moves.size(), CoreMatchers.is(0));
    }

}