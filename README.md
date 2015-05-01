## unicode-tools
Tools for selecting data from unicode database

### UnicodeBitmap class
Generates a C bitmap table for specified Unicode property

```
usage: UnicodeBitmap <UProperty> [--tests]
```

where &lt;UProperty&gt; is *EastAsianWidth* or *ALPHABETIC* or another field of UProperty class

"--tests" argument also generates test cases for that property

### Using gradle run task
Need to pass the arguments as a Java property exec.args

```
gradle run -Dexec.args="ALPHABETIC --tests"
gradle run -Dexec.args="EastAsianWidth -- tests"
```
