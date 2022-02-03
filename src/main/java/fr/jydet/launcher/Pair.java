package fr.jydet.launcher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class Pair<A, B> {
    public A a;
    public B b;
}
