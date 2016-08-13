<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8"
%><html>
<head>
  <meta http-equiv="Content-Type" content="text/html;charset=UTF-8" />
  <title>Hello, World!</title>
</head>
<body>
    <h1>Hello, World!</h1>
    <h2>Java version: <code><%=System.getProperty("java.version")%></code></h2>
    <canvas id="myCanvas" style="padding: 24px"></canvas>
    <script type="text/javascript">
        var canvas = document.getElementById('myCanvas');
        var context = canvas.getContext("2d");
        context.fillStyle = '#A6CAF0 ';
        context.font = 'italic 30px sans-serif';
        context.textBaseline='top';
        context.fillText('Hello World!', 0, 0);
        context.font = 'bold 30px sans-serif';
        context.fillText('Java version: <%=System.getProperty("java.version")%>', 0, 50);  
    </script>
</body>
</html>