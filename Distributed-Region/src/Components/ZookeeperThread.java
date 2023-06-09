package Components;

import java.util.Iterator;

import CATALOGMANAGER.CatalogManager;
import Connection.Connect;
import src.components.Master;

// 用于连接zookeeper，汇报监听端口，同时持续监听zookeeper信息（广播/任命）
public class ZookeeperThread implements Runnable {

    private int ZookeeperPort;
    private String ZookeeperIP;
    private int regionListenPort;
    private Connect zookeeper_connector;

    public ZookeeperThread(String ip, int port, int regionListenPort) {
        this.ZookeeperIP = ip;
        this.ZookeeperPort = port;
        this.regionListenPort = regionListenPort;
    }

    public void run() {
        try {
            zookeeper_connector = new Connect(ZookeeperIP, ZookeeperPort, "zookeeper");
            if (!zookeeper_connector.connect()) {
                System.out.println("<zookeeper>no zookeeper alive, connection failed");
                System.exit(1);
            }
            System.out.println("<zookeeper>connect zookeeper OK");
            zookeeper_connector.send("region:" + regionListenPort);
            while (true) {
                String result = zookeeper_connector.receive();
                if (result != null) {
                    // 更换Master节点, 数据格式master_change:ip:port
                    if (result.startsWith("change")) {
                        System.out.println(String.format("<zookeeper>master change to %s!", result));
                        String[] parts = result.split(":");
                        // 切断与当前master的连接
                        Region.masterThread.stop();
                        // 连接到新的master
                        Region.masterThread = new MasterThread(parts[1], Integer.parseInt(parts[2]), regionListenPort);
                        new Thread(Region.masterThread).start();
                    }
                    // 成为新的master, 数据格式master
                    else if (result.startsWith("toMaster")) {
                        System.out.println("<zookeeper>you're the new master!");
                        // 处理其它事情
                        // 1.new一个master线程
                        // 2.new的时候利用CatalogManager.get_table()获取表信息并传入构造函数
                        // 3.杀掉以下线程
                        // 自己线程一定要杀
                        // Region.masterThread 杀掉

                        String tableString = "";
                        Iterator<String> iterator = CatalogManager.tables.keySet().iterator();
                        while (iterator.hasNext()) {
                            String key = iterator.next();
                            tableString += key;
                            if (iterator.hasNext()) {
                                tableString += ",";
                            }
                        }
                        // 关闭与master的长连接
                        Region.masterThread.stop();
                        // 关闭Region线程的端口监听
                        Region.alive = false;
                        // 关闭与zookeeper的长连接
                        zookeeper_connector.close();
                        // 新建一个master线程并启动
                        new Master(tableString, ZookeeperIP, ZookeeperPort, 8086).start();
                        // 准备结束当前线程
                        break;
                    } else if (result.equals("ERROR")) {
                        System.out.println("<Zookeeper>Zookeeper error");
                        System.exit(1);
                    }
                    // 初始连接
                    else {
                        String[] parts = result.split(":");
                        if (parts[0] == "null") {
                            continue;
                        }
                        Region.masterThread = new MasterThread(parts[0], Integer.parseInt(parts[1]), regionListenPort);
                        new Thread(Region.masterThread).start();
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
