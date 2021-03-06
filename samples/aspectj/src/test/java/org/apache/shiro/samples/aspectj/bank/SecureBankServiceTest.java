/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shiro.samples.aspectj.bank;

import junit.framework.Assert;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.Factory;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecureBankServiceTest {

    private static Logger logger = LoggerFactory.getLogger(SecureBankServiceTest.class);
    private static SecureBankService service;
    private static int testCounter;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Factory<SecurityManager> factory = new IniSecurityManagerFactory("classpath:shiroBankServiceTest.ini");
        SecurityManager securityManager = factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);

        service = new SecureBankService();
        service.start();
    }

    @AfterClass
    public static void tearDownClass() {
        if (service != null) {
            service.dispose();
        }
    }

    private Subject _subject;

    @Before
    public void setUp() throws Exception {
        logger.info("\n\n#########################\n### STARTING TEST CASE " + (++testCounter) + "\n");
        Thread.sleep(50);
    }

    @After
    public void tearDown() {
        if (_subject != null) {
            _subject.logout();
        }
    }

    protected void logoutCurrentSubject() {
        if (_subject != null) {
            _subject.logout();
        }
    }

    protected void loginAsUser() {
        if (_subject == null) {
            _subject = SecurityUtils.getSubject();
        }

        // use dan to run as a normal user (which cannot close an account)
        _subject.login(new UsernamePasswordToken("dan", "123"));
    }

    protected void loginAsSuperviser() {
        if (_subject == null) {
            _subject = SecurityUtils.getSubject();
        }

        // use sally to run as a superviser (which cannot operate an account)
        _subject.login(new UsernamePasswordToken("sally", "1234"));
    }

    @Test
    public void testCreateAccount() throws Exception {
        loginAsUser();
        createAndValidateAccountFor("Bob Smith");
    }

    @Test
    public void testDepositInto_singleTx() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Joe Smith");
        makeDepositAndValidateAccount(accountId, 250.00d, "Joe Smith");
    }

    @Test
    public void testDepositInto_multiTxs() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Everett Smith");
        makeDepositAndValidateAccount(accountId, 50.00d, "Everett Smith");
        makeDepositAndValidateAccount(accountId, 300.00d, "Everett Smith");
        makeDepositAndValidateAccount(accountId, 85.00d, "Everett Smith");
        assertAccount("Everett Smith", true, 435.00d, 3, accountId);
    }

    @Test(expected = NotEnoughFundsException.class)
    public void testWithdrawFrom_emptyAccount() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Wally Smith");
        service.withdrawFrom(accountId, 100.00d);
    }

    @Test(expected = NotEnoughFundsException.class)
    public void testWithdrawFrom_notEnoughFunds() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Frank Smith");
        makeDepositAndValidateAccount(accountId, 50.00d, "Frank Smith");
        service.withdrawFrom(accountId, 100.00d);
    }

    @Test
    public void testWithdrawFrom_singleTx() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Al Smith");
        makeDepositAndValidateAccount(accountId, 500.00d, "Al Smith");
        makeWithdrawalAndValidateAccount(accountId, 100.00d, "Al Smith");
        assertAccount("Al Smith", true, 400.00d, 2, accountId);
    }

    @Test
    public void testWithdrawFrom_manyTxs() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Zoe Smith");
        makeDepositAndValidateAccount(accountId, 500.00d, "Zoe Smith");
        makeWithdrawalAndValidateAccount(accountId, 100.00d, "Zoe Smith");
        makeWithdrawalAndValidateAccount(accountId, 75.00d, "Zoe Smith");
        makeWithdrawalAndValidateAccount(accountId, 125.00d, "Zoe Smith");
        assertAccount("Zoe Smith", true, 200.00d, 4, accountId);
    }

    @Test
    public void testWithdrawFrom_upToZero() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Zoe Smith");
        makeDepositAndValidateAccount(accountId, 500.00d, "Zoe Smith");
        makeWithdrawalAndValidateAccount(accountId, 500.00d, "Zoe Smith");
        assertAccount("Zoe Smith", true, 0.00d, 2, accountId);
    }

    @Test
    public void testCloseAccount_zeroBalance() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Chris Smith");

        logoutCurrentSubject();
        loginAsSuperviser();
        double closingBalance = service.closeAccount(accountId);
        Assert.assertEquals(0.00d, closingBalance);
        assertAccount("Chris Smith", false, 0.00d, 1, accountId);
    }

    @Test
    public void testCloseAccount_withBalance() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Gerry Smith");
        makeDepositAndValidateAccount(accountId, 385.00d, "Gerry Smith");

        logoutCurrentSubject();
        loginAsSuperviser();
        double closingBalance = service.closeAccount(accountId);
        Assert.assertEquals(385.00d, closingBalance);
        assertAccount("Gerry Smith", false, 0.00d, 2, accountId);
    }

    @Test(expected = InactiveAccountException.class)
    public void testCloseAccount_alreadyClosed() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Chris Smith");

        logoutCurrentSubject();
        loginAsSuperviser();
        double closingBalance = service.closeAccount(accountId);
        Assert.assertEquals(0.00d, closingBalance);
        assertAccount("Chris Smith", false, 0.00d, 1, accountId);
        service.closeAccount(accountId);
    }

    @Test(expected = UnauthorizedException.class)
    public void testCloseAccount_unauthorizedAttempt() throws Exception {
        loginAsUser();
        long accountId = createAndValidateAccountFor("Chris Smith");
        service.closeAccount(accountId);
    }

    protected long createAndValidateAccountFor(String anOwner) throws Exception {
        long createdId = service.createNewAccount(anOwner);
        assertAccount(anOwner, true, 0.0d, 0, createdId);
        return createdId;
    }

    protected double makeDepositAndValidateAccount(long anAccountId, double anAmount, String eOwnerName) throws Exception {
        double previousBalance = service.getBalanceOf(anAccountId);
        int previousTxCount = service.getTxHistoryFor(anAccountId).length;
        double newBalance = service.depositInto(anAccountId, anAmount);
        Assert.assertEquals(previousBalance + anAmount, newBalance);
        assertAccount(eOwnerName, true, newBalance, 1 + previousTxCount, anAccountId);
        return newBalance;
    }

    protected double makeWithdrawalAndValidateAccount(long anAccountId, double anAmount, String eOwnerName) throws Exception {
        double previousBalance = service.getBalanceOf(anAccountId);
        int previousTxCount = service.getTxHistoryFor(anAccountId).length;
        double newBalance = service.withdrawFrom(anAccountId, anAmount);
        Assert.assertEquals(previousBalance - anAmount, newBalance);
        assertAccount(eOwnerName, true, newBalance, 1 + previousTxCount, anAccountId);
        return newBalance;
    }


    public static void assertAccount(String eOwnerName, boolean eIsActive, double eBalance, int eTxLogCount, long actualAccountId) throws Exception {
        Assert.assertEquals(eOwnerName, service.getOwnerOf(actualAccountId));
        Assert.assertEquals(eIsActive, service.isAccountActive(actualAccountId));
        Assert.assertEquals(eBalance, service.getBalanceOf(actualAccountId));
        Assert.assertEquals(eTxLogCount, service.getTxHistoryFor(actualAccountId).length);
    }
}
