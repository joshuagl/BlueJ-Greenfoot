import greenfoot.World;
import greenfoot.Actor;

public class CircleTurtle extends Turtle
{
    public CircleTurtle()
    {
        penDown();
    }

    public void act()
    {
        move(5);
        turn(2);
    }
}